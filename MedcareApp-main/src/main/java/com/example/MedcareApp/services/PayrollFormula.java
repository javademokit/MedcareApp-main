package com.example.MedcareApp.services;

import java.math.BigDecimal;
import java.math.MathContext;

public final class PayrollFormula {
    private PayrollFormula() {}

    public static BigDecimal evaluate(String formula, BigDecimal basic, BigDecimal units) {
        if (formula == null || formula.isBlank()) {
            throw new IllegalArgumentException("Formula is required");
        }
        Parser parser = new Parser(formula, basic, units);
        BigDecimal result = parser.expression();
        parser.skipSpaces();
        if (!parser.atEnd()) throw new IllegalArgumentException("Unexpected token in payroll formula");
        return PayrollCalculator.round(result);
    }

    private static final class Parser {
        private final String input;
        private final BigDecimal basic;
        private final BigDecimal units;
        private int position;

        private Parser(String input, BigDecimal basic, BigDecimal units) {
            this.input = input.toUpperCase(java.util.Locale.ROOT);
            this.basic = basic;
            this.units = units;
        }

        private BigDecimal expression() {
            BigDecimal value = term();
            while (true) {
                skipSpaces();
                if (take('+')) value = value.add(term());
                else if (take('-')) value = value.subtract(term());
                else return value;
            }
        }

        private BigDecimal term() {
            BigDecimal value = factor();
            while (true) {
                skipSpaces();
                if (take('*')) value = value.multiply(factor());
                else if (take('/')) {
                    BigDecimal divisor = factor();
                    if (divisor.signum() == 0) throw new IllegalArgumentException("Formula cannot divide by zero");
                    value = value.divide(divisor, MathContext.DECIMAL128);
                } else return value;
            }
        }

        private BigDecimal factor() {
            skipSpaces();
            if (take('+')) return factor();
            if (take('-')) return factor().negate();
            if (take('(')) {
                BigDecimal value = expression();
                skipSpaces();
                if (!take(')')) throw new IllegalArgumentException("Formula is missing a closing parenthesis");
                return value;
            }
            if (position < input.length() && Character.isLetter(input.charAt(position))) {
                int start = position;
                while (position < input.length() && Character.isLetter(input.charAt(position))) position++;
                return switch (input.substring(start, position)) {
                    case "BASIC" -> basic;
                    case "UNITS" -> units;
                    default -> throw new IllegalArgumentException("Only BASIC and UNITS are allowed in formulas");
                };
            }
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
            if (start == position) throw new IllegalArgumentException("Expected a number in payroll formula");
            return new BigDecimal(input.substring(start, position));
        }

        private boolean take(char expected) {
            if (position >= input.length() || input.charAt(position) != expected) return false;
            position++;
            return true;
        }

        private void skipSpaces() {
            while (position < input.length() && Character.isWhitespace(input.charAt(position))) position++;
        }

        private boolean atEnd() {
            return position >= input.length();
        }
    }
}
