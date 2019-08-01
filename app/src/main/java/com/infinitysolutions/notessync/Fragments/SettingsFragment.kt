package com.infinitysolutions.notessync.Fragments


import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context.MODE_PRIVATE
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.infinitysolutions.notessync.Contracts.Contract
import com.infinitysolutions.notessync.Contracts.Contract.Companion.AUTO_SYNC_WORK_ID
import com.infinitysolutions.notessync.Contracts.Contract.Companion.CLOUD_DROPBOX
import com.infinitysolutions.notessync.Contracts.Contract.Companion.CLOUD_GOOGLE_DRIVE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.MODE_CHANGE_PASSWORD
import com.infinitysolutions.notessync.Contracts.Contract.Companion.MODE_NEW_PASSWORD
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PASSWORD_MODE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_ACCESS_TOKEN
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_CLOUD_TYPE
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_ENCRYPTED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_IS_AUTO_SYNC_ENABLED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_THEME
import com.infinitysolutions.notessync.Contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.Util.WorkSchedulerHelper
import kotlinx.android.synthetic.main.fragment_settings.view.*

class SettingsFragment : Fragment() {
    private val TAG = "SettingsFragment"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_settings, container, false)
        setupViews(rootView)
        return rootView
    }

    private fun setupViews(rootView: View) {
        val toolbar = rootView.toolbar
        toolbar.title = "Settings"
        toolbar.setNavigationOnClickListener {
            activity?.onBackPressed()
        }

        val nightModeToggle = rootView.night_mode_toggle
        val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        if (prefs!!.contains(PREF_THEME))
            nightModeToggle.isChecked = prefs.getInt(PREF_THEME, 0) == 1

        nightModeToggle.setOnCheckedChangeListener { _, isChecked ->
            val editor = prefs.edit()
            if (isChecked)
                editor.putInt(PREF_THEME, 1)
            else
                editor.putInt(PREF_THEME, 0)

            editor.commit()
            updateWidgets()
            activity?.recreate()
        }

        rootView.night_mode_button.setOnClickListener {
            nightModeToggle.toggle()
        }

        rootView.about_button.setOnClickListener {
            Navigation.findNavController(rootView).navigate(R.id.action_settingsFragment_to_aboutFragment)
        }

        rootView.resources_button.setOnClickListener {
            Navigation.findNavController(rootView).navigate(R.id.action_settingsFragment_to_resourcesFragment)
        }

        rootView.open_source_button.setOnClickListener {
            openLink("https://github.com/KumarManas04/NotesSync")
        }

        val loginStatus = getLoginStatus(prefs)
        if (loginStatus != -1) {
            if (loginStatus == CLOUD_DROPBOX) {
                rootView.logout_text.text = getString(R.string.dropbox_logout_text)
                rootView.logout_button.setOnClickListener {
                    AlertDialog.Builder(context)
                        .setTitle("Logout")
                        .setMessage("Are you sure you want to logout from your Dropbox account?")
                        .setPositiveButton("Yes") { _: DialogInterface, _: Int ->
                            val editor = prefs.edit()
                            editor.putString(PREF_ACCESS_TOKEN, null)
                            editor.remove(PREF_CLOUD_TYPE)
                            editor.commit()
                            resetLoginButton(rootView)
                        }
                        .setNegativeButton("No", null)
                        .show()
                }
            } else {
                rootView.logout_text.text = getString(R.string.gdrive_logout_text)
                rootView.logout_button.setOnClickListener {
                    AlertDialog.Builder(context)
                        .setTitle("Logout")
                        .setMessage("Are you sure you want to logout from your Google Drive account?")
                        .setPositiveButton("Yes") { _: DialogInterface, _: Int ->
                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                            val googleSignInClient = GoogleSignIn.getClient(activity!!, gso)
                            googleSignInClient.signOut()
                            resetLoginButton(rootView)
                        }
                        .setNegativeButton("No", null)
                        .show()
                }
            }

            configureAutoSync(rootView, prefs)
            configureChangePassButton(rootView)
        } else {
            resetLoginButton(rootView)
        }
    }

    private fun configureChangePassButton(rootView: View){
        //This will only be reached when user is logged in
        val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        if (prefs != null){
            val passwordMode = if (prefs.contains(PREF_ENCRYPTED) && prefs.getBoolean(PREF_ENCRYPTED, false)){
                rootView.change_pass_title.text = "Change Password"
                rootView.change_pass_text.text = "Change the password used to encrypt your data in the cloud."
                MODE_CHANGE_PASSWORD
            }else{
                rootView.change_pass_title.text = "Enable encrypted sync"
                rootView.change_pass_text.text = "Set a sync password to encrypt your data in the cloud. This is to improve privacy."
                MODE_NEW_PASSWORD
            }

            rootView.change_pass_button.setOnClickListener {
                val bundle = Bundle()
                bundle.putInt(PREF_CLOUD_TYPE, prefs.getInt(PREF_CLOUD_TYPE, CLOUD_GOOGLE_DRIVE))
                bundle.putString(Contract.PREF_ID, prefs.getString(Contract.PREF_ID, null))
                bundle.putInt(PASSWORD_MODE, passwordMode)
                Navigation.findNavController(rootView).navigate(R.id.action_settingsFragment_to_passwordFragment, bundle)
            }
        }
    }

    private fun configureAutoSync(rootView: View, prefs: SharedPreferences){
        if (prefs.contains(PREF_IS_AUTO_SYNC_ENABLED))
            rootView.auto_sync_toggle.isChecked = prefs.getBoolean(PREF_IS_AUTO_SYNC_ENABLED, false)

        rootView.auto_sync_toggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                WorkSchedulerHelper().setAutoSync()
                prefs.edit().putBoolean(PREF_IS_AUTO_SYNC_ENABLED, true).commit()
            } else {
                WorkSchedulerHelper().cancelUniqueWork(AUTO_SYNC_WORK_ID)
                prefs.edit().putBoolean(PREF_IS_AUTO_SYNC_ENABLED, false).commit()
            }
        }

        rootView.auto_sync_button.setOnClickListener {
            rootView.auto_sync_toggle.toggle()
        }
    }

    private fun resetLoginButton(rootView: View) {
        rootView.logout_title.text = getString(R.string.login)
        rootView.logout_text.text = getString(R.string.login_pref_summary)
        rootView.logout_icon.setImageResource(R.drawable.lock_pref_icon)
        WorkSchedulerHelper().cancelUniqueWork(AUTO_SYNC_WORK_ID)
        val prefs = activity?.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE)
        prefs?.edit()?.putBoolean(PREF_IS_AUTO_SYNC_ENABLED, false)?.commit()
        rootView.auto_sync_toggle.isChecked = false
        rootView.logout_button.setOnClickListener {
            Navigation.findNavController(rootView).navigate(R.id.action_settingsFragment_to_cloudPickerFragment)
        }

        rootView.change_pass_title.text = "Enable encrypted sync"
        rootView.change_pass_text.text = "Set a sync password to encrypt your data in the cloud. This is to improve privacy."
        rootView.change_pass_button.setOnClickListener {
            Toast.makeText(activity, "Please login first", LENGTH_SHORT).show()
        }

        rootView.auto_sync_button.setOnClickListener {
            Toast.makeText(activity, "Please login first", LENGTH_SHORT).show()
        }
        rootView.auto_sync_toggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                rootView.auto_sync_toggle.isChecked = false
                Toast.makeText(activity, "Please login first", LENGTH_SHORT).show()
            }else{
                Toast.makeText(activity, "Please login first", LENGTH_SHORT).show()
            }
        }
    }

    private fun getLoginStatus(prefs: SharedPreferences?): Int {
        if (prefs != null && prefs.contains(PREF_CLOUD_TYPE)) {
            if (prefs.getInt(PREF_CLOUD_TYPE, CLOUD_GOOGLE_DRIVE) == CLOUD_DROPBOX) {
                if (prefs.contains(PREF_ACCESS_TOKEN) && prefs.getString(PREF_ACCESS_TOKEN, null) != null)
                    return CLOUD_DROPBOX
            } else {
                if (GoogleSignIn.getLastSignedInAccount(activity) != null)
                    return CLOUD_GOOGLE_DRIVE
            }
        }
        return -1
    }

    private fun openLink(link: String){
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
        if (browserIntent.resolveActivity(activity!!.packageManager) != null)
            startActivity(browserIntent)
        else
            Toast.makeText(activity, "No browser found!", Toast.LENGTH_SHORT).show()
    }

    private fun updateWidgets() {
        val intent = Intent(activity, NotesWidget::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        val ids =
            AppWidgetManager.getInstance(activity).getAppWidgetIds(ComponentName(activity!!, NotesWidget::class.java))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        activity?.sendBroadcast(intent)
    }
}