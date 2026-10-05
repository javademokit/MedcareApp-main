package com.example.MedcareApp.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public final class PayrollFormula {
    private PayrollFormula() {}

    public static BigDecimal evaluate(String formula, Map<String, BigDecimal> variables) {
        if (formula == null || formula.isBlank() || formula.length() > 256) {
            throw new IllegalArgumentException("Formula must contain at most 256 characters");
        }
        Parser parser = new Parser(formula, variables);
        BigDecimal result = parser.expression();
        parser.skipWhitespace();
        if (!parser.atEnd()) throw new IllegalArgumentException("Unexpected token in payroll formula");
        return result.setScale(2, RoundingMode.HALF_UP);
    }

    private static final class Parser {
        private final String input;
        private final Map<String, BigDecimal> variables;
        private int position;

        private Parser(String input, Map<String, BigDecimal> variables) {
            this.input = input;
            this.variables = variables;
        }

        private BigDecimal expression() {
            BigDecimal value = term();
            while (true) {
                skipWhitespace();
                if (take('+')) value = value.add(term());
                else if (take('-')) value = value.subtract(term());
                else return value;
            }
        }

        private BigDecimal term() {
            BigDecimal value = factor();
            while (true) {
                skipWhitespace();
                if (take('*')) value = value.multiply(factor());
                else if (take('/')) {
                    BigDecimal divisor = factor();
                    if (divisor.signum() == 0) throw new IllegalArgumentException("Formula cannot divide by zero");
                    value = value.divide(divisor, 8, RoundingMode.HALF_UP);
                } else return value;
            }
        }

        private BigDecimal factor() {
            skipWhitespace();
            if (take('+')) return factor();
            if (take('-')) return factor().negate();
            if (take('(')) {
                BigDecimal nested = expression();
                skipWhitespace();
                if (!take(')')) throw new IllegalArgumentException("Unclosed parentheses in payroll formula");
                return nested;
            }
            if (position < input.length() && Character.isDigit(input.charAt(position))) return number();
            if (position < input.length() && Character.isLetter(input.charAt(position))) return variable();
            throw new IllegalArgumentException("Expected a number or variable in payroll formula");
        }

        private BigDecimal number() {
            int start = position;
            boolean decimal = false;
            while (position < input.length()) {
                char current = input.charAt(position);
                if (Character.isDigit(current)) position++;
                else if (current == '.' && !decimal) {
                    decimal = true;
                    position++;
                } else break;
            }
            try {
                return new BigDecimal(input.substring(start, position));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid number in payroll formula");
            }
        }

        private BigDecimal variable() {
            int start = position;
            while (position < input.length()
                    && (Character.isLetterOrDigit(input.charAt(position)) || input.charAt(position) == '_')) {
                position++;
            }
            String name = input.substring(start, position).toUpperCase();
            BigDecimal value = variables.get(name);
            if (value == null) throw new IllegalArgumentException("Unknown payroll formula variable: " + name);
            return value;
        }

        private boolean take(char expected) {
            if (position < input.length() && input.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (position < input.length() && Character.isWhitespace(input.charAt(position))) position++;
        }

        private boolean atEnd() {
            return position >= input.length();
        }
    }
}
