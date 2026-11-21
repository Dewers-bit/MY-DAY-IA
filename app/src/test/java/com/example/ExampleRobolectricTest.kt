package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.FinanceRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("My Day", appName)
  }

  @Test
  fun `admin email check restricts access to dewersmatari@gmail_com`() {
    assertTrue(FinanceRepository.isAdminEmail("dewersmatari@gmail.com"))
    assertTrue(FinanceRepository.isAdminEmail("  DewersMatari@gmail.com "))
    assertFalse(FinanceRepository.isAdminEmail("outro@gmail.com"))
    assertFalse(FinanceRepository.isAdminEmail(""))
    assertFalse(FinanceRepository.isAdminEmail(null))
  }
}
