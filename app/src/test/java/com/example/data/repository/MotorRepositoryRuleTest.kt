package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.Motor
import com.example.data.model.Pinout
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class MotorRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun saveMotor_superAdmin_createsDocumentAndReturnsId() = runBlocking {
    val uid = signInTestUser(SUPER_ADMIN_EMAIL)
    val repository = MotorRepository(firestore)

    val motor = Motor(
      id = "test_motor_${UUID.randomUUID()}",
      userId = uid,
      name = "ماتور سامسونج تجريبي",
      brand = "سامسونج",
      model = "WW80",
      motorType = "Universal",
      pinouts = listOf(
        Pinout(pinNumber = 1, name = "تاكو 1", wireColor = "أصفر", colorHex = "#EAB308")
      )
    )

    val result = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.saveMotor(motor)
    }
    assertTrue(result.isSuccess)
    val savedId = result.getOrThrow()
    assertNotNull(savedId)
  }

  @Test
  fun saveMotor_regularUser_failsWithPermissionDenied() = runBlocking {
    val uid = signInTestUser(REGULAR_USER_EMAIL)
    val repository = MotorRepository(firestore)

    val motor = Motor(
      id = "test_motor_reg_${UUID.randomUUID()}",
      userId = uid,
      name = "ماتور غير مسموح",
      brand = "مجهول",
      model = "X1",
      motorType = "Universal",
      pinouts = emptyList()
    )

    val result = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.saveMotor(motor)
    }
    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull()
    assertTrue(exception is FirebaseFirestoreException)
    assertEquals(
      FirebaseFirestoreException.Code.PERMISSION_DENIED,
      (exception as FirebaseFirestoreException).code
    )
  }

  @Test
  fun observeMotors_regularUser_canReadCatalog() = runBlocking {
    signInTestUser(REGULAR_USER_EMAIL)
    val regRepo = MotorRepository(firestore)

    val emittedMotors = withTimeout(FLOW_TIMEOUT_MS) {
      regRepo.observeMotors().first()
    }
    assertNotNull(emittedMotors)
  }

  @Test
  fun adminRepository_superAdmin_canAddDynamicAdmin() = runBlocking {
    signInTestUser(SUPER_ADMIN_EMAIL)
    val adminRepo = AdminRepository(firestore, auth)

    val dynamicEmail = "new.tech.admin@example.com"
    val addResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      adminRepo.addAdmin(dynamicEmail, SUPER_ADMIN_EMAIL)
    }
    println("DEBUG_ADMIN: isSuccess=${addResult.isSuccess}, error=${addResult.exceptionOrNull()}")
    assertTrue(addResult.isSuccess)

    val isAdmin = adminRepo.checkIsAdmin(dynamicEmail)
    assertTrue(isAdmin)
  }

  private companion object {
    const val SUPER_ADMIN_EMAIL = "nwnwraldynaltantawy@gmail.com"
    const val REGULAR_USER_EMAIL = "regular.tech@washermotors.test"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
