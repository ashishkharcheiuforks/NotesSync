package com.infinitysolutions.notessync.Adapters

import android.content.Context
import android.content.Intent
import android.os.Binder
import android.view.View.GONE
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.infinitysolutions.notessync.Contracts.Contract.Companion.LIST_DEFAULT
import com.infinitysolutions.notessync.Contracts.Contract.Companion.NOTE_ID_EXTRA
import com.infinitysolutions.notessync.Contracts.Contract.Companion.PREF_THEME
import com.infinitysolutions.notessync.Contracts.Contract.Companion.SHARED_PREFS_NAME
import com.infinitysolutions.notessync.Contracts.Contract.Companion.THEME_AMOLED
import com.infinitysolutions.notessync.Contracts.Contract.Companion.THEME_DARK
import com.infinitysolutions.notessync.Contracts.Contract.Companion.THEME_DEFAULT
import com.infinitysolutions.notessync.Model.Note
import com.infinitysolutions.notessync.Model.NotesRoomDatabase
import com.infinitysolutions.notessync.R
import com.infinitysolutions.notessync.Util.ChecklistConverter

class WidgetRemoteViewsFactory(private val context: Context) :
    RemoteViewsService.RemoteViewsFactory {
    private lateinit var notesList: List<Note>
    private var selectedLayout = R.layout.widget_notes_item

    override fun getViewAt(position: Int): RemoteViews {
        val rv = RemoteViews(context.packageName, selectedLayout)
        val noteTitle = notesList[position].noteTitle
        if (noteTitle != null && noteTitle.isNotEmpty())
            rv.setTextViewText(R.id.title_text, notesList[position].noteTitle)
        else
            rv.setViewVisibility(R.id.title_text, GONE)

        var noteContent = notesList[position].noteContent
        if (noteContent != null) {
            if (notesList[position].noteType == LIST_DEFAULT && (noteContent.contains("[ ]") || noteContent.contains("[x]")))
                noteContent = ChecklistConverter.convertList(noteContent)
            rv.setTextViewText(R.id.content_preview_text, noteContent)
        }

        val fillInIntent = Intent()
        fillInIntent.putExtra(NOTE_ID_EXTRA, notesList[position].nId)
        rv.setOnClickFillInIntent(R.id.list_item_container, fillInIntent)
        return rv
    }

    override fun getCount(): Int {
        return notesList.size
    }

    override fun onCreate() {
    }

    override fun getLoadingView(): RemoteViews? {
        return null
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun onDataSetChanged() {
        val prefs = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(PREF_THEME))
            selectedLayout = when(prefs.getInt(PREF_THEME, THEME_DEFAULT)){
                THEME_DEFAULT -> R.layout.widget_notes_item
                THEME_DARK -> R.layout.widget_notes_item_dark
                THEME_AMOLED -> R.layout.widget_notes_item_amoled
                else -> R.layout.widget_notes_item
            }

        val notesDao = NotesRoomDatabase.getDatabase(context).notesDao()
        val identityToken = Binder.clearCallingIdentity()
        notesList = notesDao.getAllPresent()
        Binder.restoreCallingIdentity(identityToken)
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    override fun getViewTypeCount(): Int {
        return 2
    }

    override fun onDestroy() {}
}