package com.platinum.ott.domain.usecase

import com.platinum.ott.data.local.dao.FavoritesDao
import com.platinum.ott.data.local.entity.FavoriteEntity
import com.platinum.ott.data.local.entity.FolderEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FavoritesUseCaseTest {
    private lateinit var dao: FavoritesDao
    private lateinit var useCase: FavoritesUseCase

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
        useCase = FavoritesUseCase(dao)
    }

    @Test
    fun `toggle inserts when item is not yet a favorite`() = runTest {
        val fav = FavoriteEntity(contentId = "yt_1", title = "Матрица")
        coEvery { dao.isFavorite("yt_1") } returns false

        useCase.toggle(fav)

        coVerify(exactly = 1) { dao.insertFavorite(fav) }
        coVerify(exactly = 0) { dao.deleteByContentId(any()) }
    }

    @Test
    fun `toggle deletes when item is already a favorite`() = runTest {
        // Это branch, который проще всего перепутать местами (insert
        // вместо delete) — toggle читается как "переключить", сам факт
        // направления переключения нигде больше не проверяется.
        val fav = FavoriteEntity(contentId = "yt_1", title = "Матрица")
        coEvery { dao.isFavorite("yt_1") } returns true

        useCase.toggle(fav)

        coVerify(exactly = 1) { dao.deleteByContentId("yt_1") }
        coVerify(exactly = 0) { dao.insertFavorite(any()) }
    }

    @Test
    fun `moveToFolder forwards folderId including null for unfiled`() = runTest {
        // null означает "убрать из папки" (см. PROMPT_FAVORITES_FOLDERS.md) —
        // важно, что null действительно долетает до DAO, а не отбрасывается
        // как "ничего не передано".
        useCase.moveToFolder("yt_1", null)
        coVerify(exactly = 1) { dao.moveToFolder("yt_1", null) }

        useCase.moveToFolder("yt_1", 5L)
        coVerify(exactly = 1) { dao.moveToFolder("yt_1", 5L) }
    }

    @Test
    fun `createFolder is a direct pass-through to dao`() = runTest {
        val folder = FolderEntity(name = "Ужасы")

        useCase.createFolder(folder)
        coVerify(exactly = 1) { dao.insertFolder(folder) }
    }

    @Test
    fun `deleteFolder deletes contents together with the folder`() = runTest {
        // Решено при обсуждении PROMPT_FAVORITES_FOLDERS.md: удаление папки
        // удаляет и её содержимое (не переносит в "без папки") — использует
        // dao.deleteFolderWithContents(), а не голый dao.deleteFolder().
        val folder = FolderEntity(id = 5L, name = "Ужасы")

        useCase.deleteFolder(folder)

        coVerify(exactly = 1) { dao.deleteFolderWithContents(folder) }
    }

    @Test
    fun `setAnime forwards contentId and flag to dao`() = runTest {
        useCase.setAnime("yt_1", true)
        coVerify(exactly = 1) { dao.setAnime("yt_1", true) }
    }

    @Test
    fun `getByContentId is a direct pass-through to dao`() = runTest {
        val fav = FavoriteEntity(contentId = "yt_1", title = "Матрица", isAnime = true)
        coEvery { dao.getFavoriteByContentId("yt_1") } returns fav

        val result = useCase.getByContentId("yt_1")

        assert(result == fav)
    }
}
