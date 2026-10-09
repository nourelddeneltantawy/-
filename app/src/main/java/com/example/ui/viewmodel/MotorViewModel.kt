package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdminRecord
import com.example.data.model.Motor
import com.example.data.model.Pinout
import com.example.data.repository.AdminRepository
import com.example.data.repository.MotorRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class UserRole {
    REGULAR,
    ADMIN,
    SUPER_ADMIN
}

sealed interface MotorsUiState {
    data object Loading : MotorsUiState
    data class Success(val motors: List<Motor>) : MotorsUiState
    data class Error(val message: String) : MotorsUiState
}

class MotorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MotorRepository(application)
    private val adminRepository = AdminRepository(application)
    private val auth = Firebase.auth

    val currentUserId: String?
        get() = auth.currentUser?.uid

    val currentUserEmail: String?
        get() = auth.currentUser?.email

    private val _userRole = MutableStateFlow(UserRole.REGULAR)
    val userRole: StateFlow<UserRole> = _userRole.asStateFlow()

    val isAdmin: Boolean
        get() = _userRole.value == UserRole.ADMIN || _userRole.value == UserRole.SUPER_ADMIN

    val isSuperAdmin: Boolean
        get() = _userRole.value == UserRole.SUPER_ADMIN

    val motorsState: StateFlow<MotorsUiState> = repository.observeMotors()
        .map<List<Motor>, MotorsUiState> { MotorsUiState.Success(it) }
        .catch { emit(MotorsUiState.Error(it.message ?: "فشل في تحميل المواتير")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = MotorsUiState.Loading
        )

    val adminsList: StateFlow<List<AdminRecord>> = adminRepository.observeAdmins()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedBrand = MutableStateFlow("الكل")
    val selectedBrand = _selectedBrand.asStateFlow()

    private val _selectedMotorType = MutableStateFlow("الكل")
    val selectedMotorType = _selectedMotorType.asStateFlow()

    private val _selectedMotor = MutableStateFlow<Motor?>(null)
    val selectedMotor = _selectedMotor.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage = _actionMessage.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    init {
        checkUserRoleAndInitialize()
    }

    fun refreshUserRole() {
        checkUserRoleAndInitialize()
    }

    private fun checkUserRoleAndInitialize() {
        viewModelScope.launch {
            val email = currentUserEmail
            if (email != null) {
                if (AdminRepository.isSuperAdminEmail(email)) {
                    _userRole.value = UserRole.SUPER_ADMIN
                    adminRepository.bootstrapSuperAdminsIfMissing()
                    try {
                        repository.seedDefaultMotorsIfEmpty()
                    } catch (_: Exception) {}
                } else {
                    val isDynamic = adminRepository.checkIsAdmin(email)
                    if (isDynamic) {
                        _userRole.value = UserRole.ADMIN
                        try {
                            repository.seedDefaultMotorsIfEmpty()
                        } catch (_: Exception) {}
                    } else {
                        _userRole.value = UserRole.REGULAR
                    }
                }
            } else {
                _userRole.value = UserRole.REGULAR
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onBrandSelected(brand: String) {
        _selectedBrand.value = brand
    }

    fun onMotorTypeSelected(type: String) {
        _selectedMotorType.value = type
    }

    fun selectMotor(motor: Motor?) {
        _selectedMotor.value = motor
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun saveMotor(
        motor: Motor,
        onSuccess: (String) -> Unit = {}
    ) {
        if (!isAdmin) {
            _actionMessage.value = "عفواً، لا تملك صلاحية المشرف للتعديل أو الإضافة"
            return
        }

        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.saveMotor(motor)
            _isSaving.value = false
            if (result.isSuccess) {
                _actionMessage.value = "تم حفظ ونشر الماتور بنجاح!"
                _selectedMotor.value = motor
                onSuccess(result.getOrThrow())
            } else {
                _actionMessage.value = "خطأ في الحفظ: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun deleteMotor(
        motorId: String,
        onSuccess: () -> Unit = {}
    ) {
        if (!isAdmin) {
            _actionMessage.value = "عفواً، لا تملك صلاحية المشرف للحذف"
            return
        }

        viewModelScope.launch {
            val result = repository.deleteMotor(motorId)
            if (result.isSuccess) {
                _actionMessage.value = "تم حذف الماتور بنجاح"
                if (_selectedMotor.value?.id == motorId) {
                    _selectedMotor.value = null
                }
                onSuccess()
            } else {
                _actionMessage.value = "فشل الحذف: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    // Dynamic Admin Management (Super Admin Only)
    fun addAdmin(
        email: String,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        if (!isSuperAdmin) {
            val err = Result.failure<Unit>(IllegalAccessException("إضافة المشرفين مقتصرة فقط على المشرفين الرئيسيين (Super Admins)"))
            onComplete(err)
            return
        }

        viewModelScope.launch {
            val addedBy = currentUserEmail ?: "Super Admin"
            val result = adminRepository.addAdmin(email, addedBy)
            if (result.isSuccess) {
                _actionMessage.value = "تمت إضافة المشرف $email بنجاح إلى القائمة البيضاء"
            }
            onComplete(result)
        }
    }

    fun removeAdmin(
        email: String,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        if (!isSuperAdmin) {
            val err = Result.failure<Unit>(IllegalAccessException("إلغاء صلاحيات المشرفين مقتصرة فقط على المشرفين الرئيسيين"))
            onComplete(err)
            return
        }

        viewModelScope.launch {
            val result = adminRepository.removeAdmin(email)
            if (result.isSuccess) {
                _actionMessage.value = "تم سحب صلاحيات الإشراف من $email"
            }
            onComplete(result)
        }
    }
}
