package app.lawnchair.search.algorithms.data.calculator.internal

import app.lawnchair.search.algorithms.data.calculator.ExpressionException
import app.lawnchair.search.algorithms.data.calculator.internal.TokenType.AMP_AMP
import app.lawnchair.search.algorithms.data.calculator.internal.TokenType.ASSIGN
import app.lawnchair.search.algorithms.data.calculator.internal.TokenType.BAR_BAR
import app.lawnchair.search.algorithms.data.calculator.internal.TokenType.COMMA
import app.lawnchair.search.algorithms.data.calculator.internal.TokenType.EOF
import app.lawnchair.search.algorithms.data.calculator.internal.TokenType.EQUAL_EQUAL
import app.lawnchair.search.algorithms.data.calculator.internal.TokenType.EXPONENT
import app.lawnchair.search.algorithms.data.calculator.internal.TokenType.GREATER
import app.lawnchair.search.algorithms.data.calculator.internal.TokenType.GREATER_EQUAL
import app.lawnchair.search.algorithms.data.calculator.internal.TokenType.IDENTIFIER
import app.lawnchair.search.algorithms.data.calculator.internal.TokenType.LEFT_PAREN
import app.lawnchair.search.algorithms.data.calculator.internal.TokenType.LESS
import app.lawnchair.search.algorithms.data.calculator.internal.TokenType.LESS_EQUAL
import app.lawnchair.search.algorithms.data.calculator.internal.TokenType.MINUS
import app.lawnchair.search.algorithms.data.calculator.internal.TokenType.MODULO
import app.lawnchair.search.algorithms.data.calculator.internal.TokenType.NOT_EQUAL
import app.lawnchair.search.algorithms.data.calculator.internal.TokenType.NUMBER
import app.lawnchair.search.algorithms.data.calculator.internal.TokenType.PLUS
import app.lawnchair.search.algorithms.data.calculator.internal.TokenType.RIGHT_PAREN
import app.lawnchair.search.algorithms.data.calculator.internal.TokenType.SLASH
import app.lawnchair.search.algorithms.data.calculator.internal.TokenType.SQUARE_ROOT
import app.lawnchair.search.algorithms.data.calculator.internal.TokenType.STAR
import java.math.MathContext

private fun invalidToken(c: Char) {
    throw ExpressionException("Invalid token '$c'")
}

internal class Scanner(
    private val source: String,
    private val mathContext: MathContext,
) {

    private val tokens: MutableList<Token> = mutableListOf()
    private var start = 0
    private var current = 0

    fun scanTokens(): List<Token> {
        while (!isAtEnd()) {
            scanToken()
        }

        tokens.add(Token(EOF, "", null))
        return tokens
    }

    private fun isAtEnd(): Boolean {
        return current >= source.length
    }

    private fun scanToken() {
        start = current
        when (val c = advance()) {
            ' ',
            '\r',
            '\t',
            -> {
                // Ignore whitespace.
            }
            '+' -> addToken(PLUS)
            '-' -> addToken(MINUS)
            '*' -> addToken(STAR)
            '/' -> addToken(SLASH)
            '%' -> addToken(MODULO)
            '^' -> addToken(EXPONENT)
            '√' -> addToken(SQUARE_ROOT)
            '=' -> if (match('=')) addToken(EQUAL_EQUAL) else addToken(ASSIGN)
            '!' -> if (match('=')) addToken(NOT_EQUAL) else invalidToken(c)
            '>' -> if (match('=')) addToken(GREATER_EQUAL) else addToken(GREATER)
            '<' -> if (match('=')) addToken(LESS_EQUAL) else addToken(LESS)
            '|' -> if (match('|')) addToken(BAR_BAR) else invalidToken(c)
            '&' -> if (match('&')) addToken(AMP_AMP) else invalidToken(c)
            ',' -> addToken(COMMA)
            '(' -> addToken(LEFT_PAREN)
            ')' -> addToken(RIGHT_PAREN)
            else -> {
                when {
                    c.isDigit() -> number()
                    c.isAlpha() -> identifier()
                    else -> invalidToken(c)
                }
            }
        }
    }

    private fun isDigit(
        char: Char,
        previousChar: Char = '\u0000',
        nextChar: Char = '\u0000',
    ): Boolean {
        return char.isDigit() ||
            when (char) {
                '.' -> true
                'e', 'E' -> previousChar.isDigit() && (nextChar.isDigit() || nextChar == '+' || nextChar == '-')
                '+', '-' -> (previousChar == 'e' || previousChar == 'E') && nextChar.isDigit()
                else -> false
            }
    }

    private fun number() {
        while (peek().isDigit()) advance()

        if (isDigit(peek(), peekPrevious(), peekNext())) {
            advance()
            while (isDigit(peek(), peekPrevious(), peekNext())) advance()
        }

        val value = source
            .substring(start, current)
            .toBigDecimal(mathContext)

        addToken(NUMBER, value)
    }

    private fun identifier() {
        while (peek().isAlphaNumeric()) advance()

        addToken(IDENTIFIER)
    }

    private fun advance() = source[current++]

    private fun peek(): Char {
        return if (isAtEnd()) {
            '\u0000'
        } else {
            source[current]
        }
    }

    private fun peekPrevious(): Char = if (current > 0) source[current - 1] else '\u0000'

    private fun peekNext(): Char {
        return if (current + 1 >= source.length) {
            '\u0000'
        } else {
            source[current + 1]
        }
    }

    private fun match(expected: Char): Boolean {
        if (isAtEnd()) return false
        if (source[current] != expected) return false

        current++
        return true
    }

    private fun addToken(type: TokenType) = addToken(type, null)

    private fun addToken(type: TokenType, literal: Any?) {
        val text = source.substring(start, current)
        tokens.add(Token(type, text, literal))
    }

    private fun Char.isAlphaNumeric() = isAlpha() || isDigit()

    private fun Char.isAlpha() = this in 'a'..'z' ||
        this in 'A'..'Z' ||
        this == '_'

    private fun Char.isDigit() = this == '.' || this in '0'..'9'
}
