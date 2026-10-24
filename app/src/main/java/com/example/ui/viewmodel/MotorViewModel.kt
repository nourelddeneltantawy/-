package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdminRecord
import com.example.data.model.Motor
import com.example.data.model.MotorCategory
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
import java.util.UUID

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
        val app = com.example.util.FirebaseInitializer.ensureInitialized(getApplication())
        if (app != null) {
            try { com.google.firebase.auth.FirebaseAuth.getInstance(app) } catch (_: Throwable) { null }
        } else {
            try { Firebase.auth } catch (_: Throwable) {
                try { com.google.firebase.auth.FirebaseAuth.getInstance() } catch (_: Throwable) { null }
            }
        }
    }

    private val sharedPrefs by lazy {
        application.getSharedPreferences("washer_motor_prefs", Context.MODE_PRIVATE)
    }

    val currentUserId: String?
        get() = try { auth?.currentUser?.uid } catch (_: Throwable) { null }

    val currentUserEmail: String?
        get() = try { auth?.currentUser?.email } catch (_: Throwable) { null }

    private val _userRole = MutableStateFlow(UserRole.REGULAR)
    val userRole: StateFlow<UserRole> = _userRole.asStateFlow()

    val isAdmin: Boolean
        get() = currentUserEmail != null || _userRole.value == UserRole.ADMIN || _userRole.value == UserRole.SUPER_ADMIN

    val isSuperAdmin: Boolean
        get() = _userRole.value == UserRole.SUPER_ADMIN

    // Clean initial state - user creates from scratch!
    private val _motorsState = MutableStateFlow<MotorsUiState>(
        MotorsUiState.Success(emptyList())
    )
    val motorsState: StateFlow<MotorsUiState> = _motorsState.asStateFlow()

    // User-defined Custom Categories / Lists
    private val _categories = MutableStateFlow<List<MotorCategory>>(loadCachedCategories())
    val categories: StateFlow<List<MotorCategory>> = _categories.asStateFlow()

    private val _selectedCategory = MutableStateFlow("الكل")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // Favorites List
    private val _favorites = MutableStateFlow<Set<String>>(loadFavorites())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

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
    private var observeCategoriesJob: kotlinx.coroutines.Job? = null

    init {
        startObservingMotors()
        startObservingCategories()

        viewModelScope.launch {
            try {
                adminRepository?.observeAdmins()?.collect { list ->
                    _adminsList.value = list
                }
            } catch (_: Throwable) {}
        }

        checkUserRoleAndInitialize()
    }

    private fun loadFavorites(): Set<String> {
        return try {
            sharedPrefs.getStringSet("favorite_motors", emptySet()) ?: emptySet()
        } catch (_: Throwable) {
            emptySet()
        }
    }

    private fun saveFavorites(set: Set<String>) {
        try {
            sharedPrefs.edit().putStringSet("favorite_motors", set).apply()
        } catch (_: Throwable) {}
    }

    fun isFavorite(motorId: String): Boolean {
        return _favorites.value.contains(motorId)
    }

    fun toggleFavorite(motorId: String) {
        val current = _favorites.value.toMutableSet()
        if (current.contains(motorId)) {
            current.remove(motorId)
            _actionMessage.value = "تمت الإزالة من المفضلة"
        } else {
            current.add(motorId)
            _actionMessage.value = "تمت الإضافة إلى المفضلة ❤️"
        }
        _favorites.value = current
        saveFavorites(current)
    }

    private fun loadCachedCategories(): List<MotorCategory> {
        return try {
            val raw = sharedPrefs.getString("cached_categories", null)
            if (raw.isNullOrBlank()) {
                emptyList()
            } else {
                raw.split(";;;").filter { it.isNotBlank() }.map { name ->
                    MotorCategory(id = name, name = name)
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun saveCachedCategories(cats: List<MotorCategory>) {
        try {
            val serialized = cats.joinToString(";;;") { it.name }
            sharedPrefs.edit().putString("cached_categories", serialized).apply()
        } catch (_: Throwable) {}
    }

    fun startObservingCategories() {
        observeCategoriesJob?.cancel()
        observeCategoriesJob = viewModelScope.launch {
            try {
                repository?.observeCategories()?.collect { catList ->
                    _categories.value = catList
                    saveCachedCategories(catList)
                }
            } catch (e: Throwable) {
                android.util.Log.e("MotorViewModel", "observeCategories error", e)
            }
        }
    }

    fun addCategory(name: String, onSuccess: () -> Unit = {}) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return

        val existing = _categories.value
        if (existing.any { it.name.equals(cleanName, ignoreCase = true) }) {
            _actionMessage.value = "هذه القائمة موجودة بالفعل"
            return
        }

        val newCat = MotorCategory(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            orderIndex = existing.size
        )
        val updated = existing + newCat
        _categories.value = updated
        saveCachedCategories(updated)
        _selectedCategory.value = cleanName
        _actionMessage.value = "تمت إضافة قائمة \"$cleanName\" بنجاح"

        viewModelScope.launch {
            repository?.saveCategory(newCat)
            onSuccess()
        }
    }

    fun deleteCategory(categoryId: String) {
        val cat = _categories.value.find { it.id == categoryId || it.name == categoryId } ?: return
        val updated = _categories.value.filter { it.id != cat.id && it.name != cat.name }
        _categories.value = updated
        saveCachedCategories(updated)
        if (_selectedCategory.value == cat.name) {
            _selectedCategory.value = "الكل"
        }
        _actionMessage.value = "تم حذف القائمة \"${cat.name}\""

        viewModelScope.launch {
            repository?.deleteCategory(cat.id)
        }
    }

    fun onCategorySelected(category: String) {
        _selectedCategory.value = category
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
                    } else {
                        val isDynamic = try {
                            adminRepository?.checkIsAdmin(cleanEmail) ?: false
                        } catch (_: Throwable) {
                            false
                        }
                        if (isDynamic) {
                            _userRole.value = UserRole.ADMIN
                        } else {
                            _userRole.value = UserRole.REGULAR
                        }
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
                _actionMessage.value = "تم حفظ الماتور بنجاح!"
                _selectedMotor.value = motor

                // Auto add motor category to user categories if not present
                if (motor.category.isNotBlank() && _categories.value.none { it.name.equals(motor.category, ignoreCase = true) }) {
                    addCategory(motor.category)
                }

                val assignedId = result.getOrThrow()
                val finalMotor = if (motor.id.isNotBlank()) motor else motor.copy(id = assignedId)
                _selectedMotor.value = finalMotor

                // Optimistically update list
                val current = _motorsState.value
                if (current is MotorsUiState.Success) {
                    val updatedList = current.motors.toMutableList()
                    val idx = if (motor.id.isNotBlank()) updatedList.indexOfFirst { it.id == motor.id } else -1
                    if (idx >= 0) {
                        updatedList[idx] = finalMotor
                    } else {
                        updatedList.add(0, finalMotor)
                    }
                    _motorsState.value = MotorsUiState.Success(updatedList)
                }

                onSuccess(assignedId)
            } else {
                _actionMessage.value = "خطأ في الحفظ: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun deleteMotor(
        motorId: String,
        onSuccess: () -> Unit = {}
    ) {
        // Immediate optimistic UI removal so motor vanishes instantly without delay!
        val current = _motorsState.value
        if (current is MotorsUiState.Success) {
            val updated = current.motors.filter { it.id != motorId }
            _motorsState.value = MotorsUiState.Success(updated)
        }

        if (_selectedMotor.value?.id == motorId) {
            _selectedMotor.value = null
        }

        if (_favorites.value.contains(motorId)) {
            val newFavs = _favorites.value.filter { it != motorId }.toSet()
            _favorites.value = newFavs
            saveFavorites(newFavs)
        }

        _actionMessage.value = "تم حذف الماتور بنجاح"
        onSuccess()

        viewModelScope.launch {
            val repo = repository
            if (repo != null) {
                val result = repo.deleteMotor(motorId)
                if (result.isFailure) {
                    android.util.Log.e("MotorViewModel", "Remote delete failed: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    fun moveMotor(motorId: String, moveUp: Boolean) {
        val currentState = _motorsState.value
        if (currentState !is MotorsUiState.Success) return
        val list = currentState.motors.toMutableList()
        val index = list.indexOfFirst { it.id == motorId }
        if (index == -1) return
        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex < 0 || targetIndex >= list.size) return

        val item = list.removeAt(index)
        list.add(targetIndex, item)
        val updatedList = list.mapIndexed { idx, m -> m.copy(orderIndex = idx) }
        _motorsState.value = MotorsUiState.Success(updatedList)

        viewModelScope.launch {
            repository?.updateMotorsOrder(updatedList)
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
