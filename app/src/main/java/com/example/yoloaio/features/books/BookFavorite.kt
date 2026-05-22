package com.example.yoloaio.features.books

/**
 * Firestore-friendly snapshot of a Book. Stored at
 * `users/{uid}/favoriteBooks/{id}` so it survives across devices and
 * mirrors the wallpaper / ringtone favorites pattern.
 *
 * Needs an empty constructor (default args do that) for Firestore's
 * automatic deserializer.
 */
data class BookFavorite(
    val id: String = "",
    val bookId: String = "",
    val title: String = "",
    val authors: String = "",
    val coverUrl: String = "",
    val textUrl: String = "",
    val htmlUrl: String = "",
    val epubUrl: String = "",
    val downloadCount: Int = 0,
    val addedAt: Long = 0L
) {
    fun toBook(): Book = Book(
        id = bookId,
        title = title,
        authors = authors,
        coverUrl = coverUrl,
        textUrl = textUrl,
        htmlUrl = htmlUrl,
        epubUrl = epubUrl,
        downloadCount = downloadCount
    )

    companion object {
        fun fromBook(book: Book): BookFavorite = BookFavorite(
            id = book.id,
            bookId = book.id,
            title = book.title,
            authors = book.authors,
            coverUrl = book.coverUrl,
            textUrl = book.textUrl,
            htmlUrl = book.htmlUrl,
            epubUrl = book.epubUrl,
            downloadCount = book.downloadCount,
            addedAt = System.currentTimeMillis()
        )
    }
}
