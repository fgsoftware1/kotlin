/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.text

// actually \s is enough to match all whitespace, but \xA0 added because of different regexp behavior of Rhino used in Selenium tests
public actual fun Char.isWhitespace(): Boolean = toString().matches("[\\s\\xA0]")

@kotlin.internal.InlineOnly
public actual inline fun Char.toLowerCase(): Char = js("String.fromCharCode")(toInt()).toLowerCase().charCodeAt(0).unsafeCast<Int>().toChar()

@kotlin.internal.InlineOnly
public actual inline fun Char.toUpperCase(): Char = js("String.fromCharCode")(toInt()).toUpperCase().charCodeAt(0).unsafeCast<Int>().toChar()

/**
 * Returns `true` if this character is a Unicode high-surrogate code unit (also known as leading-surrogate code unit).
 */
public actual fun Char.isHighSurrogate(): Boolean = this in Char.MIN_HIGH_SURROGATE..Char.MAX_HIGH_SURROGATE

/**
 * Returns `true` if this character is a Unicode low-surrogate code unit (also known as trailing-surrogate code unit).
 */
public actual fun Char.isLowSurrogate(): Boolean = this in Char.MIN_LOW_SURROGATE..Char.MAX_LOW_SURROGATE

/**
 * Return a Unicode category of this character as an Int.
 */
@kotlin.internal.InlineOnly
internal actual inline fun Char.getCategoryValue(): Int = getCategoryValue(this.toInt())

/**
 * Returns `true` if this character (Unicode code point) is defined in Unicode.
 */
public actual fun Char.isDefined(): Boolean = getCategoryValue() != CharCategory.UNASSIGNED.value

/**
 * Returns `true` if this character is a letter.
 * @sample samples.text.Chars.isLetter
 */
public actual fun Char.isLetter(): Boolean = getCategoryValue() in CharCategory.UPPERCASE_LETTER.value..CharCategory.OTHER_LETTER.value

/**
 * Returns `true` if this character is a letter or digit.
 * @sample samples.text.Chars.isLetterOrDigit
 */
public actual fun Char.isLetterOrDigit(): Boolean = isLetter() || isDigit()

/**
 * Returns `true` if this character (Unicode code point) is a digit.
 * @sample samples.text.Chars.isDigit
 */
public actual fun Char.isDigit(): Boolean = getCategoryValue() == CharCategory.DECIMAL_DIGIT_NUMBER.value

/**
 * Returns `true` if this character is upper case.
 * @sample samples.text.Chars.isUpperCase
 */
public actual fun Char.isUpperCase(): Boolean = getCategoryValue() == CharCategory.UPPERCASE_LETTER.value

/**
 * Returns `true` if this character is lower case.
 * @sample samples.text.Chars.isLowerCase
 */
public actual fun Char.isLowerCase(): Boolean = getCategoryValue() == CharCategory.LOWERCASE_LETTER.value

/**
 * Returns `true` if this character is a titlecase character.
 * @sample samples.text.Chars.isTitleCase
 */
public actual fun Char.isTitleCase(): Boolean = getCategoryValue() == CharCategory.TITLECASE_LETTER.value