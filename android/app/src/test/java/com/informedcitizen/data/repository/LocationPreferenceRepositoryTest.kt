package com.informedcitizen.data.repository

import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import okio.FileSystem
import okio.FileSystem.Companion.asOkioFileSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.FileSystems

@OptIn(ExperimentalCoroutinesApi::class)
class LocationPreferenceRepositoryTest {
    @get:Rule val tempFolder = TemporaryFolder()

    private fun newRepo(scope: TestScope): LocationPreferenceRepository {
        // Use OkioStorage backed by the NIO file system rather than the default
        // File-based storage. The default FileStorage uses File.renameTo for
        // back-to-back writes, which cannot overwrite an existing file on
        // Windows; the NIO-backed Okio file system uses Files.move with
        // ATOMIC_MOVE | REPLACE_EXISTING which works on every platform.
        val nioFs: FileSystem = FileSystems.getDefault().asOkioFileSystem()
        val storage = OkioStorage(
            fileSystem = nioFs,
            serializer = PreferencesSerializer,
            producePath = {
                File(tempFolder.newFolder(), "loc_prefs.preferences_pb")
                    .absoluteFile.toOkioPath()
            },
        )
        val store = PreferenceDataStoreFactory.create(storage = storage, scope = scope)
        return LocationPreferenceRepository(store)
    }

    @Test
    fun defaultIsNoLocation() = runTest(UnconfinedTestDispatcher()) {
        val repo = newRepo(this)
        val loc = repo.location.first()
        assertNull(loc.stateCode)
        assertNull(loc.district)
    }

    @Test
    fun setLocationRoundTrips() = runTest(UnconfinedTestDispatcher()) {
        val repo = newRepo(this)
        repo.set(stateCode = "TX", district = 21)
        val loc = repo.location.first()
        assertEquals("TX", loc.stateCode)
        assertEquals(21, loc.district)
    }

    @Test
    fun setLocationNullDistrictPersists() = runTest(UnconfinedTestDispatcher()) {
        val repo = newRepo(this)
        repo.set(stateCode = "DC", district = null)
        val loc = repo.location.first()
        assertEquals("DC", loc.stateCode)
        assertNull(loc.district)
    }

    @Test
    fun forgetClearsBoth() = runTest(UnconfinedTestDispatcher()) {
        val repo = newRepo(this)
        repo.set(stateCode = "TX", district = 21)
        repo.forget()
        val loc = repo.location.first()
        assertNull(loc.stateCode)
        assertNull(loc.district)
    }
}
