package com.example.util

/**
 * Mini-calculator parser for transaction input.
 * Evaluates expressions like "15000+8000+12000", "50000 - 15000", "25000*2", "100000/2"
 * and returns the calculated Long value.
 */
object MathExpressionEvaluator {

    fun evaluateMathExpression(input: String): Long {
        val sanitized = sanitize(input)
        if (sanitized.isEmpty()) return 0L

        return try {
            val result = parseExpression(sanitized)
            if (result.isFinite()) result.toLong().coerceAtLeast(0L) else 0L
        } catch (_: Exception) {
            // Fallback: try plain number parsing
            val digitsOnly = sanitized.filter { it.isDigit() }
            digitsOnly.toLongOrNull() ?: 0L
        }
    }

    /**
     * Checks if the input is a valid mathematical expression with at least one operator.
     */
    fun isCompoundExpression(input: String): Boolean {
        val cleaned = input.replace(" ", "")
        return cleaned.any { it == '+' || it == '-' || it == '*' || it == '/' || it == 'x' || it == 'X' || it == ':' }
    }

    private fun sanitize(input: String): String {
        return input
            .replace(" ", "")
            .replace("x", "*")
            .replace("X", "*")
            .replace(":", "/")
            // Remove dots only if used as thousand separators (e.g. 15.000)
            .replace(".", "")
            .replace(",", ".")
    }

    private fun parseExpression(expression: String): Double {
        var pos = -1
        var ch = 0

        fun nextChar() {
            pos++
            ch = if (pos < expression.length) expression[pos].code else -1
        }

        fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parseFactor(): Double {
            if (eat('+'.code)) return +parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression(expression.substring(startPos + 1))
                eat(')'.code)
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = expression.substring(startPos, pos).toDouble()
            } else {
                return 0.0
            }

            return x
        }

        fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> {
                        val divisor = parseFactor()
                        x = if (divisor != 0.0) x / divisor else 0.0
                    }
                    else -> return x
                }
            }
        }

        fun parse(): Double {
            nextChar()
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        return parse()
    }
}
