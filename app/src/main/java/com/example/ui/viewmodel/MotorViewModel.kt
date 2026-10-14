package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DefaultMotorsData
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

    private val repository by lazy {
        try {
            MotorRepository(getApplication())
        } catch (_: Throwable) {
            null
        }
    }

    private val adminRepository by lazy {
        try {
            AdminRepository(getApplication())
        } catch (_: Throwable) {
            null
        }
    }

    private val auth by lazy {
        try {
            Firebase.auth
        } catch (_: Throwable) {
            com.google.firebase.auth.FirebaseAuth.getInstance()
        }
    }

    val currentUserId: String?
        get() = try { auth.currentUser?.uid } catch (_: Throwable) { null }

    val currentUserEmail: String?
        get() = try { auth.currentUser?.email } catch (_: Throwable) { null }

    private val _userRole = MutableStateFlow(UserRole.REGULAR)
    val userRole: StateFlow<UserRole> = _userRole.asStateFlow()

    val isAdmin: Boolean
        get() = _userRole.value == UserRole.ADMIN || _userRole.value == UserRole.SUPER_ADMIN

    val isSuperAdmin: Boolean
        get() = _userRole.value == UserRole.SUPER_ADMIN

    private val _motorsState = MutableStateFlow<MotorsUiState>(
        MotorsUiState.Success(DefaultMotorsData.getDefaultMotors("guest"))
    )
    val motorsState: StateFlow<MotorsUiState> = _motorsState.asStateFlow()

    private val _adminsList = MutableStateFlow<List<AdminRecord>>(emptyList())
    val adminsList: StateFlow<List<AdminRecord>> = _adminsList.asStateFlow()

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

    private var observeJob: kotlinx.coroutines.Job? = null

    init {
        startObservingMotors()

        viewModelScope.launch {
            try {
                adminRepository?.observeAdmins()?.collect { list ->
                    _adminsList.value = list
                }
            } catch (_: Throwable) {}
        }

        checkUserRoleAndInitialize()
    }

    fun startObservingMotors() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            try {
                repository?.observeMotors()?.collect { motorsList ->
                    _motorsState.value = MotorsUiState.Success(motorsList)
                }
            } catch (e: Throwable) {
                android.util.Log.e("MotorViewModel", "Error in startObservingMotors", e)
            }
        }
    }

    fun refreshUserRole(emailOverride: String? = null) {
        val targetEmail = emailOverride ?: currentUserEmail
        if (targetEmail != null) {
            val cleanEmail = targetEmail.trim().lowercase()
            if (AdminRepository.isSuperAdminEmail(cleanEmail)) {
                _userRole.value = UserRole.SUPER_ADMIN
            } else {
                _userRole.value = UserRole.ADMIN
            }
        }
        checkUserRoleAndInitialize(emailOverride)
        startObservingMotors()
    }

    fun checkUserRoleAndInitialize(emailOverride: String? = null) {
        viewModelScope.launch {
            try {
                val email = emailOverride ?: currentUserEmail
                if (email != null) {
                    val cleanEmail = email.trim().lowercase()
                    if (AdminRepository.isSuperAdminEmail(cleanEmail)) {
                        _userRole.value = UserRole.SUPER_ADMIN
                        try {
                            adminRepository?.bootstrapSuperAdminsIfMissing()
                        } catch (_: Throwable) {}
                        try {
                            repository?.seedDefaultMotorsIfEmpty()
                        } catch (_: Throwable) {}
                    } else {
                        _userRole.value = UserRole.ADMIN
                        try {
                            repository?.seedDefaultMotorsIfEmpty()
                        } catch (_: Throwable) {}
                    }
                } else {
                    _userRole.value = UserRole.REGULAR
                }
            } catch (_: Throwable) {
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
            val repo = repository
            if (repo == null) {
                _isSaving.value = false
                _actionMessage.value = "خدمة التخزين السحابي غير متصلة حالياً"
                return@launch
            }
            val result = repo.saveMotor(motor)
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
            val repo = repository
            if (repo == null) {
                _actionMessage.value = "خدمة التخزين السحابي غير متصلة حالياً"
                return@launch
            }
            val result = repo.deleteMotor(motorId)
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
            val adminRepo = adminRepository
            if (adminRepo == null) {
                onComplete(Result.failure(IllegalStateException("خدمة إدارة المشرفين غير متصلة")))
                return@launch
            }
            val addedBy = currentUserEmail ?: "Super Admin"
            val result = adminRepo.addAdmin(email, addedBy)
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
            val adminRepo = adminRepository
            if (adminRepo == null) {
                onComplete(Result.failure(IllegalStateException("خدمة إدارة المشرفين غير متصلة")))
                return@launch
            }
            val result = adminRepo.removeAdmin(email)
            if (result.isSuccess) {
                _actionMessage.value = "تم سحب صلاحيات الإشراف من $email"
            }
            onComplete(result)
        }
    }
}
