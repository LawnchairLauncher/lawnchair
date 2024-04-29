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
import java.math.BigDecimal

internal class Parser(private val tokens: List<Token>) {

    private var current = 0

    fun parse(): Expr {
        val expr = expression()

        if (!isAtEnd()) {
            throw ExpressionException("Expected end of expression, found '${peek().lexeme}'")
        }

        return expr
    }

    private fun expression(): Expr {
        return assignment()
    }

    private fun assignment(): Expr {
        val expr = or()

        if (match(ASSIGN)) {
            val value = assignment()

            if (expr is VariableExpr) {
                val name = expr.name

                return AssignExpr(name, value)
            } else {
                throw ExpressionException("Invalid assignment target")
            }
        }

        return expr
    }

    private fun or(): Expr {
        var expr = and()

        while (match(BAR_BAR)) {
            val operator = previous()
            val right = and()

            expr = LogicalExpr(expr, operator, right)
        }

        return expr
    }

    private fun and(): Expr {
        var expr = equality()

        while (match(AMP_AMP)) {
            val operator = previous()
            val right = equality()

            expr = LogicalExpr(expr, operator, right)
        }

        return expr
    }

    private fun equality(): Expr {
        var left = comparison()

        while (match(EQUAL_EQUAL, NOT_EQUAL)) {
            val operator = previous()
            val right = comparison()

            left = BinaryExpr(left, operator, right)
        }

        return left
    }

    private fun comparison(): Expr {
        var left = addition()

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            val operator = previous()
            val right = addition()

            left = BinaryExpr(left, operator, right)
        }

        return left
    }

    private fun addition(): Expr {
        var left = multiplication()

        while (match(PLUS, MINUS)) {
            val operator = previous()
            val right = multiplication()

            left = BinaryExpr(left, operator, right)
        }

        return left
    }

    private fun multiplication(): Expr {
        var left = unary()

        while (match(STAR, SLASH, MODULO)) {
            val operator = previous()
            val right = unary()

            left = BinaryExpr(left, operator, right)
        }

        return left
    }

    private fun unary(): Expr {
        if (match(MINUS)) {
            val operator = previous()
            val right = unary()

            return UnaryExpr(operator, right)
        }

        return sqrt()
    }

    private fun sqrt(): Expr {
        if (match(SQUARE_ROOT)) {
            val operator = previous()
            val right = unary()

            return UnaryExpr(operator, right)
        }

        return exponent()
    }

    private fun exponent(): Expr {
        var left = call()

        if (match(EXPONENT)) {
            val operator = previous()
            val right = unary()

            left = BinaryExpr(left, operator, right)
        }

        return left
    }

    private fun call(): Expr {
        if (matchTwo(IDENTIFIER, LEFT_PAREN)) {
            val (name, _) = previousTwo()

            val arguments = mutableListOf<Expr>()

            if (!check(RIGHT_PAREN)) {
                do {
                    arguments += expression()
                } while (match(COMMA))
            }

            consume(RIGHT_PAREN, "Expected ')' after function arguments")

            return CallExpr(name.lexeme, arguments)
        }

        return primary()
    }

    private fun primary(): Expr {
        if (match(NUMBER)) {
            return LiteralExpr(previous().literal as BigDecimal)
        }

        if (match(IDENTIFIER)) {
            return VariableExpr(previous())
        }

        if (match(LEFT_PAREN)) {
            val expr = expression()

            consume(RIGHT_PAREN, "Expected ')' after '${previous().lexeme}'.")

            return GroupingExpr(expr)
        }

        throw ExpressionException("Expected expression after '${previous().lexeme}'.")
    }

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()

                return true
            }
        }

        return false
    }

    private fun matchTwo(first: TokenType, second: TokenType): Boolean {
        val start = current

        if (match(first) && match(second)) {
            return true
        }

        current = start
        return false
    }

    private fun check(tokenType: TokenType): Boolean {
        return if (isAtEnd()) {
            false
        } else {
            peek().type === tokenType
        }
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()

        throw ExpressionException(message)
    }

    private fun advance(): Token {
        if (!isAtEnd()) current++

        return previous()
    }

    private fun isAtEnd() = peek().type == EOF

    private fun peek() = tokens[current]

    private fun previous() = tokens[current - 1]

    private fun previousTwo() = Pair(tokens[current - 2], tokens[current - 1])
}
