package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SupervisorAccount
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.data.repository.FinanceRepository
import com.example.data.repository.ThemePreferencesRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BudgetScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DebtsScreen
import com.example.ui.screens.ExpensesScreen
import com.example.ui.screens.IncomesScreen
import com.example.ui.screens.MyDayPlannerScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SalaryScreen
import com.example.ui.screens.SavingsScreen
import com.example.ui.screens.signOutUser
import com.example.ui.theme.EmeraldIncome
import com.example.ui.theme.MyDayIaTheme
import com.example.ui.viewmodel.AdminUsersCountState
import com.example.ui.viewmodel.AppDataState
import com.example.ui.viewmodel.MyDayViewModel
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

enum class AppDestination(
    val route: String,
    val title: String,
    val shortLabel: String,
    val icon: ImageVector
) {
    DASHBOARD("dashboard", "Dashboard", "Início", Icons.Rounded.Dashboard),
    EXPENSES("expenses", "Despesas", "Despesas", Icons.AutoMirrored.Rounded.ReceiptLong),
    INCOMES("incomes", "Receitas", "Receitas", Icons.Rounded.AccountBalanceWallet),
    SALARY("salary", "Meu Salário", "Salário", Icons.Rounded.Payments),
    BUDGET("budget", "Orçamento", "Orçamento", Icons.Rounded.PieChart),
    SAVINGS("savings", "Poupanças", "Poupanças", Icons.Rounded.Savings),
    DEBTS("debts", "Dívidas", "Dívidas", Icons.Rounded.CreditCard),
    MY_DAY("my_day", "My Day", "My Day", Icons.AutoMirrored.Rounded.EventNote),
    REPORTS("reports", "Relatórios", "Relatórios", Icons.Rounded.Assessment)
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val themePrefs = remember(context) { ThemePreferencesRepository(context.applicationContext) }
            val darkOverride by themePrefs.isDarkMode.collectAsStateWithLifecycle(initialValue = null)
            val isDarkTheme = darkOverride ?: isSystemInDarkTheme()
            val scope = rememberCoroutineScope()

            MyDayIaTheme(darkTheme = isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = {
                            scope.launch { themePrefs.setDarkMode(!isDarkTheme) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val isFirebaseReady = remember(context) {
        FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null
    }
    if (!isFirebaseReady) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Configuração Firebase em falta neste APK (google-services.json). Gere novamente o APK com o ficheiro google-services.json incluído.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        return
    }

    val auth: FirebaseAuth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedMyDayShell(
                user = user,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedMyDayShell(
    user: FirebaseUser,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val uid = user.uid
    val displayName = user.displayName ?: "Utilizador"
    val email = user.email ?: ""

    val viewModel: MyDayViewModel = viewModel(
        key = uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MyDayViewModel(
                    repository = FinanceRepository(db),
                    currentUserId = uid,
                    currentUserName = displayName,
                    currentUserEmail = email
                )
            }
        }
    )

    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val recentlyConfirmedCycleId by viewModel.recentlyConfirmedCycleId.collectAsStateWithLifecycle()
    val adminUsersCountState by viewModel.adminUsersCountState.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route ?: AppDestination.DASHBOARD.route
    val currentDest = AppDestination.entries.firstOrNull { it.route == currentRoute } ?: AppDestination.DASHBOARD

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var pendingCreateExpense by remember { mutableStateOf(false) }
    var pendingCreateIncome by remember { mutableStateOf(false) }
    var showAdminMonitoringDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(recentlyConfirmedCycleId) {
        if (!recentlyConfirmedCycleId.isNullOrBlank()) {
            navigateTo(AppDestination.SALARY.route)
        }
    }

    if (currentRoute != AppDestination.DASHBOARD.route) {
        BackHandler { navigateTo(AppDestination.DASHBOARD.route) }
    }

    val primaryBottomDestinations = remember {
        listOf(
            AppDestination.DASHBOARD,
            AppDestination.EXPENSES,
            AppDestination.INCOMES,
            AppDestination.SALARY,
            AppDestination.MY_DAY
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(292.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (user.photoUrl != null) {
                            AsyncImage(
                                model = user.photoUrl,
                                contentDescription = "Foto de perfil",
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldIncome.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = displayName.firstOrNull()?.uppercase() ?: "M",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = EmeraldIncome,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    AppDestination.entries.forEach { dest ->
                        NavigationDrawerItem(
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(dest.title) },
                            selected = currentRoute == dest.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navigateTo(dest.route)
                            },
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }

                    if (viewModel.isAdmin) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = stringResource(R.string.admin_section_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Rounded.SupervisorAccount, contentDescription = null) },
                            label = { Text(stringResource(R.string.admin_monitoring_title)) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                showAdminMonitoringDialog = true
                                viewModel.refreshAdminUsersCount()
                            },
                            modifier = Modifier
                                .padding(vertical = 2.dp)
                                .testTag("admin_monitoring_drawer_item")
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = null
                            )
                        },
                        label = { Text(if (isDarkTheme) "Mudar para Modo Claro" else "Mudar para Modo Escuro") },
                        selected = false,
                        onClick = onToggleTheme
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null) },
                        label = { Text("Terminar Sessão") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            signOutUser(credentialManager, scope)
                        }
                    )
                }
            }
        }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 680.dp

            Scaffold(
                contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = currentDest.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "My Day • Kwanza (Kz)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.testTag("open_drawer_button")
                            ) {
                                Icon(Icons.Rounded.Menu, contentDescription = "Abrir menu lateral")
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = onToggleTheme,
                                modifier = Modifier.testTag("toggle_theme_button")
                            ) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                    contentDescription = "Alternar modo claro/escuro"
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar {
                            primaryBottomDestinations.forEach { dest ->
                                NavigationBarItem(
                                    selected = currentRoute == dest.route,
                                    onClick = { navigateTo(dest.route) },
                                    icon = { Icon(dest.icon, contentDescription = dest.title) },
                                    label = { Text(dest.shortLabel) }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        NavigationRail {
                            AppDestination.entries.take(7).forEach { dest ->
                                NavigationRailItem(
                                    selected = currentRoute == dest.route,
                                    onClick = { navigateTo(dest.route) },
                                    icon = { Icon(dest.icon, contentDescription = dest.title) },
                                    label = { Text(dest.shortLabel) }
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        when (val state = dashboardState) {
                            AppDataState.Loading -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = EmeraldIncome)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text("A sincronizar dados financeiros...", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            is AppDataState.Error -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text(
                                            text = state.message,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }
                            }
                            is AppDataState.Success -> {
                                val data = state.data
                                NavHost(
                                    navController = navController,
                                    startDestination = AppDestination.DASHBOARD.route
                                ) {
                                    composable(AppDestination.DASHBOARD.route) {
                                        DashboardScreen(
                                            data = data,
                                            userName = displayName,
                                            onNavigateToRoute = { navigateTo(it) },
                                            onQuickAddExpense = {
                                                pendingCreateExpense = true
                                                navigateTo(AppDestination.EXPENSES.route)
                                            },
                                            onQuickAddIncome = {
                                                pendingCreateIncome = true
                                                navigateTo(AppDestination.INCOMES.route)
                                            },
                                            onConfirmSalaryReceipt = { cycleKey, desc, amt, dateMillis, nextPayMillis, notes ->
                                                viewModel.confirmSalaryReceipt(
                                                    cycleKey = cycleKey,
                                                    description = desc,
                                                    receivedAmount = amt,
                                                    receivedDateMillis = dateMillis,
                                                    nextPaydayMillis = nextPayMillis,
                                                    notes = notes
                                                )
                                            },
                                            onSnoozeSalaryPrompt = { viewModel.snoozeSalaryPrompt(it) }
                                        )
                                    }
                                    composable(AppDestination.EXPENSES.route) {
                                        ExpensesScreen(
                                            expenses = data.expenses,
                                            salarySummary = data.salarySummary,
                                            openCreateDialogImmediately = pendingCreateExpense,
                                            onConsumeCreateTrigger = { pendingCreateExpense = false },
                                            onSaveExpense = { exp ->
                                                if (exp.id.isBlank()) viewModel.addExpense(exp)
                                                else viewModel.updateExpense(exp)
                                            },
                                            onDeleteExpense = { viewModel.deleteExpense(it) }
                                        )
                                    }
                                    composable(AppDestination.INCOMES.route) {
                                        IncomesScreen(
                                            incomes = data.incomes,
                                            openCreateDialogImmediately = pendingCreateIncome,
                                            onConsumeCreateTrigger = { pendingCreateIncome = false },
                                            onSaveIncome = { inc ->
                                                if (inc.id.isBlank()) viewModel.addIncome(inc)
                                                else viewModel.updateIncome(inc)
                                            },
                                            onDeleteIncome = { viewModel.deleteIncome(it) }
                                        )
                                    }
                                    composable(AppDestination.SALARY.route) {
                                        SalaryScreen(
                                            summary = data.salarySummary,
                                            autoOpenDistributionCycleId = recentlyConfirmedCycleId,
                                            onConsumeAutoOpenDistribution = { viewModel.consumeRecentlyConfirmedCycle() },
                                            onSaveConfig = { amount, payday, freq ->
                                                viewModel.saveSalaryConfig(amount, payday, freq)
                                            },
                                            onSnoozePrompt = { viewModel.snoozeSalaryPrompt(it) },
                                            onConfirmSalaryReceipt = { cycleKey, desc, amt, dateMillis, nextPayMillis, notes ->
                                                viewModel.confirmSalaryReceipt(
                                                    cycleKey = cycleKey,
                                                    description = desc,
                                                    receivedAmount = amt,
                                                    receivedDateMillis = dateMillis,
                                                    nextPaydayMillis = nextPayMillis,
                                                    notes = notes
                                                )
                                            },
                                            onSaveAllocations = { cycleId, receivedAmt, allocs ->
                                                viewModel.updateSalaryCycleAllocations(cycleId, receivedAmt, allocs)
                                            },
                                            onUpdateCycleDetails = { viewModel.updateSalaryCycleDetails(it) },
                                            onDeleteCycle = { viewModel.deleteSalaryCycle(it) }
                                        )
                                    }
                                    composable(AppDestination.BUDGET.route) {
                                        BudgetScreen(
                                            data = data,
                                            onUpdateGlobalLimit = { viewModel.updateMonthlyBudgetLimit(it) },
                                            onSaveCategoryBudget = { viewModel.upsertCategoryBudget(it) },
                                            onDeleteCategoryBudget = { viewModel.deleteCategoryBudget(it) }
                                        )
                                    }
                                    composable(AppDestination.SAVINGS.route) {
                                        SavingsScreen(
                                            goals = data.savingsGoals,
                                            onSaveGoal = { g ->
                                                if (g.id.isBlank()) viewModel.addSavingsGoal(g)
                                                else viewModel.updateSavingsGoal(g)
                                            },
                                            onAddContribution = { g, amount -> viewModel.addSavingsContribution(g, amount) },
                                            onDeleteGoal = { viewModel.deleteSavingsGoal(it) }
                                        )
                                    }
                                    composable(AppDestination.DEBTS.route) {
                                        DebtsScreen(
                                            debts = data.debts,
                                            overdueDebts = data.overdueDebts,
                                            upcomingDebts = data.upcomingDebts,
                                            onSaveDebt = { d ->
                                                if (d.id.isBlank()) viewModel.addDebt(d)
                                                else viewModel.updateDebt(d)
                                            },
                                            onRecordPayment = { d, amt -> viewModel.recordDebtPayment(d, amt) },
                                            onToggleSettled = { viewModel.toggleDebtSettled(it) },
                                            onDeleteDebt = { viewModel.deleteDebt(it) }
                                        )
                                    }
                                    composable(AppDestination.MY_DAY.route) {
                                        MyDayPlannerScreen(
                                            tasks = data.tasks,
                                            onSaveTask = { t ->
                                                if (t.id.isBlank()) viewModel.addDailyTask(t)
                                                else viewModel.updateDailyTask(t)
                                            },
                                            onToggleCompleted = { viewModel.toggleTaskCompleted(it) },
                                            onDeleteTask = { viewModel.deleteDailyTask(it) }
                                        )
                                    }
                                    composable(AppDestination.REPORTS.route) {
                                        ReportsScreen(
                                            data = data,
                                            onExportFeedback = { msg ->
                                                scope.launch { snackbarHostState.showSnackbar(msg) }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdminMonitoringDialog && viewModel.isAdmin) {
        AdminMonitoringDialog(
            state = adminUsersCountState,
            onRefresh = { viewModel.refreshAdminUsersCount() },
            onDismiss = { showAdminMonitoringDialog = false }
        )
    }
}

@Composable
private fun AdminMonitoringDialog(
    state: AdminUsersCountState,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.SupervisorAccount,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.admin_monitoring_title),
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.admin_monitoring_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when (state) {
                    AdminUsersCountState.Idle,
                    AdminUsersCountState.Loading -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                color = EmeraldIncome
                            )
                            Text(
                                text = stringResource(R.string.admin_monitoring_loading),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    is AdminUsersCountState.Success -> {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_users_count_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = state.totalUsers.toString(),
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.admin_monitoring_users_label),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    is AdminUsersCountState.Error -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.admin_monitoring_privacy_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onRefresh,
                enabled = state !is AdminUsersCountState.Loading,
                modifier = Modifier.testTag("admin_refresh_count_button")
            ) {
                Text(stringResource(R.string.admin_monitoring_refresh))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("admin_close_dialog_button")
            ) {
                Text(stringResource(R.string.admin_monitoring_close))
            }
        }
    )
}
