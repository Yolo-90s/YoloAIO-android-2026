package com.example.yoloaio.features.quotes

object PresetQuotes {

    private fun preset(
        id: String,
        text: String,
        author: String,
        colors: List<Long>
    ) = Quote(
        id = id,
        text = text,
        author = author,
        style = QuoteStyle(
            textColor = 0xFFFFFFFFL,
            fontSize = 28,
            italic = true,
            alignment = QuoteStyle.ALIGN_CENTER,
            backgroundType = QuoteStyle.BG_GRADIENT,
            backgroundColors = colors
        ),
        isCustom = false
    )

    val all: List<Quote> = listOf(
        preset(
            "p1", "The best way to predict the future is to invent it.", "Alan Kay",
            listOf(0xFF1A237EL, 0xFF4A148CL)
        ),
        preset(
            "p2", "Stay hungry. Stay foolish.", "Steve Jobs",
            listOf(0xFFB71C1CL, 0xFFE65100L)
        ),
        preset(
            "p3", "Simplicity is the ultimate sophistication.", "Leonardo da Vinci",
            listOf(0xFF004D40L, 0xFF263238L)
        ),
        preset(
            "p4", "Whether you think you can, or you think you can't — you're right.", "Henry Ford",
            listOf(0xFF6A1B9AL, 0xFFAD1457L)
        ),
        preset(
            "p5", "Talk is cheap. Show me the code.", "Linus Torvalds",
            listOf(0xFF0D47A1L, 0xFF01579BL)
        ),
        preset(
            "p6", "It always seems impossible until it's done.", "Nelson Mandela",
            listOf(0xFF1B5E20L, 0xFF004D40L)
        ),
        preset(
            "p7", "Make it work, make it right, make it fast.", "Kent Beck",
            listOf(0xFF263238L, 0xFF000000L)
        ),
        preset(
            "p8", "If you can't explain it simply, you don't understand it well enough.",
            "Albert Einstein", listOf(0xFF311B92L, 0xFF1A237EL)
        ),
        preset(
            "p9", "Do, or do not. There is no try.", "Yoda",
            listOf(0xFF2E7D32L, 0xFF1B5E20L)
        ),
        preset(
            "p10", "The only way to do great work is to love what you do.", "Steve Jobs",
            listOf(0xFFAD1457L, 0xFF880E4FL)
        ),
        preset(
            "p11",
            "Not everything that is faced can be changed. But nothing can be changed until it is faced.",
            "James Baldwin", listOf(0xFFBF360CL, 0xFF3E2723L)
        ),
        preset(
            "p12", "Premature optimization is the root of all evil.", "Donald Knuth",
            listOf(0xFF1A237EL, 0xFF000000L)
        )
    )
}
