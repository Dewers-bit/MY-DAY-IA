const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function serverTime() {
  return new Date();
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Unauthenticated user cannot read or write any user collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("expenses").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("expenses").doc("exp_1").set({
      userId: ALICE_UID,
      description: "Almoço",
      amount: 2500,
      category: "Alimentação",
      dateMillis: Date.now(),
      paymentMethod: "Dinheiro",
      notes: "",
      isFixed: false,
      createdAt: serverTime(),
      updatedAt: serverTime(),
    })
  );
});

test("Owner can create, update, list, and delete their own expense", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb.collection("users").doc(ALICE_UID).collection("expenses").doc("exp_1");
  const created = serverTime();
  await assertSucceeds(
    ref.set({
      userId: ALICE_UID,
      description: "Táxi Luanda",
      amount: 2500,
      category: "Transporte",
      dateMillis: Date.now(),
      paymentMethod: "Dinheiro",
      notes: "Centro",
      isFixed: false,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("expenses").get());
  await assertSucceeds(
    ref.update({
      amount: 3000,
      updatedAt: serverTime(),
    })
  );
  await assertSucceeds(ref.delete());
});

test("Cross-user isolation blocks Bob from reading or writing Alice's subcollections", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const now = serverTime();
    await ctx.firestore().collection("users").doc(ALICE_UID).collection("expenses").doc("exp_a").set({
      userId: ALICE_UID,
      description: "Internet Unitel",
      amount: 10000,
      category: "Internet e comunicação",
      dateMillis: Date.now(),
      paymentMethod: "Multicaixa",
      notes: "",
      isFixed: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("expenses").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("expenses").doc("exp_a").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("expenses").doc("exp_b").set({
      userId: ALICE_UID,
      description: "Hack",
      amount: 100,
      category: "Outros",
      dateMillis: Date.now(),
      paymentMethod: "Dinheiro",
      notes: "",
      isFixed: false,
      createdAt: serverTime(),
      updatedAt: serverTime(),
    })
  );
});

test("Shadow update with ghost field and invalid negative amount are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = serverTime();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("expenses").doc("exp_ghost").set({
      userId: ALICE_UID,
      description: "Café",
      amount: 1500,
      category: "Alimentação",
      dateMillis: Date.now(),
      paymentMethod: "Dinheiro",
      notes: "",
      isFixed: false,
      ghostField: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("expenses").doc("exp_neg").set({
      userId: ALICE_UID,
      description: "Inválido",
      amount: -500,
      category: "Alimentação",
      dateMillis: Date.now(),
      paymentMethod: "Dinheiro",
      notes: "",
      isFixed: false,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("User can manage incomes, budgets, savings goals, debts, and daily tasks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = serverTime();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Ana Silva",
      email: "ana@example.com",
      monthlyBudgetLimit: 250000,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("incomes").doc("inc_1").set({
      userId: ALICE_UID,
      description: "Salário Mensal",
      amount: 350000,
      source: "Salário",
      dateMillis: Date.now(),
      incomeType: "Fixo",
      notes: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("categoryBudgets").doc("cat_food").set({
      userId: ALICE_UID,
      category: "Alimentação",
      monthlyLimit: 80000,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("savingsGoals").doc("goal_1").set({
      userId: ALICE_UID,
      name: "Reserva de emergência",
      targetAmount: 500000,
      savedAmount: 100000,
      deadlineMillis: Date.now() + 86400000 * 90,
      notes: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("debts").doc("debt_1").set({
      userId: ALICE_UID,
      title: "Empréstimo portátil",
      counterparty: "João",
      amount: 120000,
      paidAmount: 40000,
      direction: "OWE",
      debtType: "Prestação",
      dueDateMillis: Date.now() + 86400000 * 10,
      isSettled: false,
      isRecurring: false,
      notes: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("dailyTasks").doc("task_1").set({
      userId: ALICE_UID,
      title: "Pagar internet",
      notes: "Multicaixa Express",
      dateMillis: Date.now(),
      priority: "ALTA",
      itemType: "LEMBRETE",
      timeLabel: "10:00",
      isCompleted: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("salarySettings").doc("default").set({
      userId: ALICE_UID,
      monthlySalary: 350000,
      paydayOfMonth: 25,
      frequency: "Mensal",
      lastPromptedPeriodKey: "",
      snoozedUntilMillis: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("salaryCycles").doc("cycle_2026_11").set({
      userId: ALICE_UID,
      cycleKey: "2026-11",
      description: "Salário - Novembro 2026",
      receivedAmount: 350000,
      receivedDateMillis: Date.now(),
      nextPaydayMillis: Date.now() + 86400000 * 30,
      linkedIncomeId: "inc_1",
      allocations: [
        { category: "Alimentação", allocatedAmount: 80000 },
        { category: "Poupança", allocatedAmount: 50000 }
      ],
      notes: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  const msgRef = aliceDb.collection("users").doc(ALICE_UID).collection("aiMessages").doc("msg_1");
  await assertSucceeds(
    msgRef.set({
      userId: ALICE_UID,
      isUser: false,
      text: "Identifiquei uma despesa.",
      timestampMillis: Date.now(),
      pendingExpenses: [
        { description: "Táxi", amount: 2500, category: "Transporte", paymentMethod: "Dinheiro", isFixed: false }
      ],
      expensesConfirmed: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    msgRef.update({
      text: "Confirmado! 1 despesa(s) guardada(s) na sua conta.",
      expensesConfirmed: true,
      updatedAt: serverTime(),
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("aiMessages").get());

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("salarySettings").doc("default").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("salaryCycles").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("aiMessages").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("aiMessages").doc("msg_1").get());
});

test("Verified admin (dewersmatari@gmail.com) can list/count users but cannot access private user data or write profiles", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const now = serverTime();
    await ctx.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Ana Silva",
      email: "ana@example.com",
      monthlyBudgetLimit: 250000,
      createdAt: now,
      updatedAt: now,
    });
    await ctx.firestore().collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bruno",
      email: "bruno@example.com",
      monthlyBudgetLimit: 100000,
      createdAt: now,
      updatedAt: now,
    });
    await ctx.firestore().collection("users").doc(ALICE_UID).collection("expenses").doc("exp_1").set({
      userId: ALICE_UID,
      description: "Privado",
      amount: 5000,
      category: "Outros",
      dateMillis: Date.now(),
      paymentMethod: "Dinheiro",
      notes: "",
      isFixed: false,
      createdAt: now,
      updatedAt: now,
    });
  });

  const adminDb = testEnv.authenticatedContext("admin_uid", {
    email: "dewersmatari@gmail.com",
    email_verified: true,
  }).firestore();
  const usersSnap = await assertSucceeds(adminDb.collection("users").get());
  if (usersSnap.size !== 2) {
    throw new Error(`Expected 2 users, got ${usersSnap.size}`);
  }

  // Admin still cannot read single private financial subcollections or overwrite another user's profile
  await assertFails(adminDb.collection("users").doc(ALICE_UID).get());
  await assertFails(adminDb.collection("users").doc(ALICE_UID).collection("expenses").get());
  await assertFails(adminDb.collection("users").doc(ALICE_UID).collection("expenses").doc("exp_1").get());
  await assertFails(
    adminDb.collection("users").doc(ALICE_UID).update({
      monthlyBudgetLimit: 999999,
      updatedAt: serverTime(),
    })
  );

  // Unverified admin token and regular users cannot list users
  const unverifiedAdminDb = testEnv.authenticatedContext("admin_unverified", {
    email: "dewersmatari@gmail.com",
    email_verified: false,
  }).firestore();
  await assertFails(unverifiedAdminDb.collection("users").get());

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "ana@example.com",
    email_verified: true,
  }).firestore();
  await assertFails(aliceDb.collection("users").get());

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").get());
});

