package org.expreval

internal actual fun bindFixture(): String =
    requireNotNull(BindTest::class.java.getResourceAsStream("/bind.tsv")).bufferedReader().use { it.readText() }
