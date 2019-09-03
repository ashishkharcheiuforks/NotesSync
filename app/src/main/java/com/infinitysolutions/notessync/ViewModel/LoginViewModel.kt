package com.infinitysolutions.notessync.ViewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.infinitysolutions.notessync.Contracts.Contract
import com.infinitysolutions.notessync.Contracts.Contract.Companion.ENCRYPTED_CHECK_ERROR
import com.infinitysolutions.notessync.Contracts.Contract.Companion.ENCRYPTED_NO
import com.infinitysolutions.notessync.Contracts.Contract.Companion.ENCRYPTED_YES
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PASSWORD_CHANGE_NETWORK_ERROR
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PASSWORD_CHANGE_OLD_INVALID
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PASSWORD_CHANGE_SUCCESS
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PASSWORD_VERIFY_CORRECT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PASSWORD_VERIFY_ERROR
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PASSWORD_VERIFY_INVALID
import com.infinitysolutions.notessync.Model.NoteFile
import com.infinitysolutions.notessync.Util.AES256Helper
import com.infinitysolutions.notessync.Util.DropboxHelper
import com.infinitysolutions.notessync.Util.GoogleDriveHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.AEADBadTagException

class LoginViewModel : ViewModel() {

    private val encryptionCheckLoading = MutableLiveData<Boolean>()
    private val encryptionCheckResult = MutableLiveData<Int>()
    private val loadingMessage = MutableLiveData<String>()
    private val verifyPasswordResult = MutableLiveData<Int>()
    private val secureDataResult = MutableLiveData<Boolean>()
    private val changePasswordResult = MutableLiveData<Int>()
    var isLoginInitialized = false
    var encryptionDetected = false
    var isLoginSuccess = false
    lateinit var googleDriveHelper: GoogleDriveHelper
    lateinit var dropboxHelper: DropboxHelper
    private val TAG = "LoginViewModel"

    fun getEncryptionCheckLoading(): LiveData<Boolean> {
        return encryptionCheckLoading
    }

    fun getEncryptionCheckResult(): LiveData<Int> {
        return encryptionCheckResult
    }

    fun getVerifyPasswordResult(): LiveData<Int> {
        return verifyPasswordResult
    }

    fun getSecureDataResult(): LiveData<Boolean> {
        return secureDataResult
    }

    fun getLoadingMessage(): LiveData<String> {
        return loadingMessage
    }

    fun getChangePasswordResult(): LiveData<Int> {
        return changePasswordResult
    }

    fun changePassword(userId: String, cloudType: Int, oldPassword: String, newPassword: String) {
        loadingMessage.value = "Updating password..."
        viewModelScope.launch(Dispatchers.IO) {
            val aesHelper = AES256Helper()
            aesHelper.generateKey(oldPassword, userId)
            try {
                val key = getPresentCredential(aesHelper, cloudType)
                if (key != null) {
                    aesHelper.generateKey(newPassword, userId)
                    val encryptedKey = aesHelper.encrypt(key)
                    val result = uploadKey(encryptedKey, cloudType)
                    withContext(Dispatchers.Main) {
                        loadingMessage.value = null
                        changePasswordResult.value = if (result) PASSWORD_CHANGE_SUCCESS else PASSWORD_CHANGE_NETWORK_ERROR
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        loadingMessage.value = null
                        changePasswordResult.value = PASSWORD_CHANGE_OLD_INVALID
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main){
                    loadingMessage.value = null
                    changePasswordResult.value = PASSWORD_CHANGE_NETWORK_ERROR
                }
            }
        }
    }

    private fun getPresentCredential(aesHelper: AES256Helper, cloudType: Int): String? {
        var content: String? = if (cloudType == Contract.CLOUD_GOOGLE_DRIVE) {
            val credentialFileId = googleDriveHelper.searchFile(Contract.CREDENTIALS_FILENAME, Contract.FILE_TYPE_TEXT)
            if (credentialFileId != null)
                googleDriveHelper.getFileContent(credentialFileId)
            else
                null
        } else {
            if (dropboxHelper.checkIfFileExists(Contract.CREDENTIALS_FILENAME))
                dropboxHelper.getFileContent(Contract.CREDENTIALS_FILENAME)
            else
                null
        }

        try {
            if (content != null) {
                content = aesHelper.decrypt(content)
            }
        } catch (e: AEADBadTagException) {
            return null
        }
        return content
    }

    fun checkCloudEncryption(cloudType: Int) {
        encryptionCheckLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
                        try {
            val encryptionFound = checkForEncryption(cloudType)
            withContext(Dispatchers.Main) {
                encryptionCheckLoading.value = false
                if (encryptionFound)
                    encryptionCheckResult.value = ENCRYPTED_YES
                else
                    encryptionCheckResult.value = ENCRYPTED_NO
                encryptionDetected = encryptionFound
            }
            }catch (e: Exception){
                withContext(Dispatchers.Main) {
                    encryptionCheckLoading.value = false
                    encryptionCheckResult.value = ENCRYPTED_CHECK_ERROR
                    encryptionDetected = false
                }
            }
        }
    }

    private fun checkForEncryption(cloudType: Int): Boolean {
        if (cloudType == Contract.CLOUD_GOOGLE_DRIVE) {
            val credentialFileId =
                googleDriveHelper.searchFile(Contract.CREDENTIALS_FILENAME, Contract.FILE_TYPE_TEXT) ?: return false

            val content = googleDriveHelper.getFileContent(credentialFileId)
            return content != null
        } else {
            return if (dropboxHelper.checkIfFileExists(Contract.CREDENTIALS_FILENAME)) {
                val content = dropboxHelper.getFileContent(Contract.CREDENTIALS_FILENAME)
                content != null
            } else {
                false
            }
        }
    }

    fun runVerification(userId: String, cloudType: Int, password: String) {
        loadingMessage.value = "Verifying password..."
        viewModelScope.launch(Dispatchers.IO) {
            val result = verifyPassword(password, userId, cloudType)
            withContext(Dispatchers.Main) {
                loadingMessage.value = null
                if (result != null) {
                    if (result)
                        verifyPasswordResult.value = PASSWORD_VERIFY_CORRECT
                    else
                        verifyPasswordResult.value = PASSWORD_VERIFY_INVALID
                } else {
                    verifyPasswordResult.value = PASSWORD_VERIFY_ERROR
                }
            }
        }
    }

    private fun verifyPassword(password: String, userId: String, cloudType: Int): Boolean? {
        val aesHelper = AES256Helper()
        aesHelper.generateKey(password, userId)

        if (cloudType == Contract.CLOUD_GOOGLE_DRIVE) {
            val credentialFileId = googleDriveHelper.searchFile(Contract.CREDENTIALS_FILENAME, Contract.FILE_TYPE_TEXT)
            return if (credentialFileId != null) {
                val content = googleDriveHelper.getFileContent(credentialFileId)
                if (content != null) {
                    try {
                        aesHelper.decrypt(content)
                        true
                    } catch (e: AEADBadTagException) {
                        false
                    }
                } else {
                    null
                }
            } else {
                null
            }
        } else {
            return if (dropboxHelper.checkIfFileExists(Contract.CREDENTIALS_FILENAME)) {
                val content = dropboxHelper.getFileContent(Contract.CREDENTIALS_FILENAME)
                if (content != null) {
                    try {
                        aesHelper.decrypt(content)
                        true
                    } catch (e: AEADBadTagException) {
                        false
                    }
                } else {
                    null
                }
            } else {
                null
            }
        }
    }

    fun secureCloudData(userId: String, cloudType: Int, password: String) {
        loadingMessage.value = "Securing your data..."
        viewModelScope.launch(Dispatchers.IO) {
            val secureRandom = SecureRandom()
            val key = ByteArray(32)
            secureRandom.nextBytes(key)

            //Encrypt all user data in cloud storage
            val encryptionResult = encryptAllCloudData(cloudType, userId, key.toString())

            //Encrypt the encryption key
            val aesHelper = AES256Helper()
            aesHelper.generateKey(password, userId)
            val encryptedKey = aesHelper.encrypt(key.toString())
            val result = uploadKey(encryptedKey, cloudType)
            withContext(Dispatchers.Main) {
                loadingMessage.value = null
                secureDataResult.value = result && encryptionResult
            }
        }
    }

    private fun encryptAllCloudData(cloudType: Int, userId: String, password: String): Boolean {
        return try {
            val aesHelper = AES256Helper()
            aesHelper.generateKey(password, userId)
            val filesList = if (cloudType == Contract.CLOUD_GOOGLE_DRIVE) {
                getFilesListGD(googleDriveHelper.fileSystemId)
            } else {
                getFilesListDB()
            }
            uploadFiles(filesList, aesHelper, cloudType)
            true
        } catch (e: java.lang.Exception) {
            false
        }
    }

    private fun uploadKey(encryptedKey: String, cloudType: Int): Boolean {
        return try {
            if (cloudType == Contract.CLOUD_GOOGLE_DRIVE) {
                val credentialFileId = googleDriveHelper.searchFile(
                    Contract.CREDENTIALS_FILENAME,
                    Contract.FILE_TYPE_TEXT
                )
                if (credentialFileId != null) {
                    googleDriveHelper.updateFile(credentialFileId, encryptedKey)
                } else {
                    googleDriveHelper.createFile(
                        googleDriveHelper.appFolderId,
                        Contract.CREDENTIALS_FILENAME,
                        Contract.FILE_TYPE_TEXT,
                        encryptedKey
                    )
                }
            } else {
                dropboxHelper.writeFile(Contract.CREDENTIALS_FILENAME, encryptedKey)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun uploadFiles(filesList: List<NoteFile>, aesHelper: AES256Helper, cloudType: Int) {
        var fileContent: String
        var fileContentEncrypted: String
        for (file in filesList) {
            if (cloudType == Contract.CLOUD_GOOGLE_DRIVE) {
                fileContent = googleDriveHelper.getFileContent(file.gDriveId) as String
                fileContentEncrypted = aesHelper.encrypt(fileContent)
                googleDriveHelper.updateFile(file.gDriveId!!, fileContentEncrypted)
            } else {
                fileContent = dropboxHelper.getFileContent("${file.nId}.txt") as String
                fileContentEncrypted = aesHelper.encrypt(fileContent)
                dropboxHelper.writeFile("${file.nId}.txt", fileContentEncrypted)
            }
        }

        val fileSystemJson = Gson().toJson(filesList)
        val fileSystemJsonEncrypted = aesHelper.encrypt(fileSystemJson)
        if (cloudType == Contract.CLOUD_GOOGLE_DRIVE) {
            googleDriveHelper.updateFile(googleDriveHelper.fileSystemId, fileSystemJsonEncrypted)
        } else {
            dropboxHelper.writeFile(Contract.FILE_SYSTEM_FILENAME, fileSystemJsonEncrypted)
        }
    }

    private fun getFilesListGD(fileSystemId: String): List<NoteFile> {
        val fileContent = googleDriveHelper.getFileContent(fileSystemId)
        return Gson().fromJson(fileContent, Array<NoteFile>::class.java).asList()
    }

    private fun getFilesListDB(): List<NoteFile> {
        return if (dropboxHelper.checkIfFileExists(Contract.FILE_SYSTEM_FILENAME)) {
            val fileContent = dropboxHelper.getFileContent(Contract.FILE_SYSTEM_FILENAME)
            Gson().fromJson(fileContent, Array<NoteFile>::class.java).asList()
        } else {
            val filesList = ArrayList<NoteFile>()
            val fileContent = Gson().toJson(filesList)
            dropboxHelper.writeFile(Contract.FILE_SYSTEM_FILENAME, fileContent)
            filesList
        }
    }

    fun resetViewModel() {
        encryptionCheckLoading.value = null
        encryptionCheckResult.value = null
        verifyPasswordResult.value = null
        loadingMessage.value = null
        secureDataResult.value = null
        isLoginInitialized = false
        encryptionDetected = false
        isLoginSuccess = false
    }
}