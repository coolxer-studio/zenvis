package com.coolxer.service.retrieval.impl;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.retrieval.dto.RequestCriteriaDto;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

class WhereExpressionParser {

    private static final Pattern FIELD_PATTERN = Pattern.compile("[A-Za-z_][\\w]*");
    private static final Map<String, String> OPERATOR_MAP = Map.of(
            "=", "equal",
            "==", "equal",
            "!=", "notequal",
            "<>", "notequal",
            ">", "greatthan",
            "<", "lessthan",
            ">=", "greatequalthan",
            "<=", "lessequalthan"
    );

    private List<Token> tokens = List.of();
    private int position = 0;

    WhereExpression parse(String expression) {
        String normalizedExpression = normalizeExpression(expression);
        this.tokens = tokenize(normalizedExpression);
        this.position = 0;
        WhereNode root = parseOrExpression();
        expect(TokenType.END, "高级where表达式格式不正确");
        List<RequestCriteriaDto> criteriaList = new ArrayList<>();
        collectCriteria(root, criteriaList);
        if (criteriaList.isEmpty()) {
            throw invalid("高级where表达式条件不能为空");
        }
        return new WhereExpression(root, root.logicOrDefault(), criteriaList, normalizedExpression);
    }

    private String normalizeExpression(String expression) {
        if (StringUtils.isBlank(expression)) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "高级where表达式不能为空");
        }
        String trimmed = expression.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("where ")) {
            trimmed = trimmed.substring("where ".length()).trim();
        }
        if (StringUtils.isBlank(trimmed)) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "高级where表达式不能为空");
        }
        return trimmed;
    }

    private WhereNode parseOrExpression() {
        WhereNode left = parseAndExpression();
        while (matchKeyword("or")) {
            left = group("or", left, parseAndExpression());
        }
        return left;
    }

    private WhereNode parseAndExpression() {
        WhereNode left = parsePrimary();
        while (matchKeyword("and")) {
            left = group("and", left, parsePrimary());
        }
        return left;
    }

    private WhereNode parsePrimary() {
        if (match(TokenType.LPAREN)) {
            WhereNode nested = parseOrExpression();
            expect(TokenType.RPAREN, "高级where表达式括号不匹配");
            return nested;
        }
        return WhereNode.condition(parseCondition());
    }

    private RequestCriteriaDto parseCondition() {
        String field = expectIdentifier("高级where表达式字段不合法");
        validateField(field);

        if (matchKeyword("between")) {
            return criteria(field, "between", List.of(parseValue(), parseBetweenSecondValue()));
        }
        if (matchKeyword("in")) {
            expect(TokenType.LPAREN, "in操作符需要使用括号");
            List<String> values = new ArrayList<>();
            values.add(parseValue());
            while (match(TokenType.COMMA)) {
                values.add(parseValue());
            }
            expect(TokenType.RPAREN, "in操作符括号不匹配");
            return criteria(field, "in", values);
        }
        if (matchKeyword("like")) {
            return criteria(field, "match", List.of(normalizeLikeValue(parseValue())));
        }
        if (matchKeyword("is")) {
            if (matchKeyword("not")) {
                expectNullKeyword();
                return criteria(field, "isnotnull", List.of());
            }
            expectNullKeyword();
            return criteria(field, "isnull", List.of());
        }

        Token operatorToken = peek();
        if (operatorToken.type() == TokenType.IDENT) {
            String operator = operatorToken.text().toLowerCase(Locale.ROOT);
            if (List.of("isnull", "isnotnull").contains(operator)) {
                position++;
                return criteria(field, operator, List.of());
            }
            if (List.of("equal", "notequal", "match", "contains", "greatthan", "lessthan", "greatequalthan", "lessequalthan").contains(operator)) {
                position++;
                return criteria(field, "contains".equals(operator) ? "match" : operator, List.of(parseValue()));
            }
        }
        if (operatorToken.type() == TokenType.OPERATOR && OPERATOR_MAP.containsKey(operatorToken.text())) {
            position++;
            return criteria(field, OPERATOR_MAP.get(operatorToken.text()), List.of(parseValue()));
        }
        throw invalid("高级where表达式操作符不正确");
    }

    private void expectNullKeyword() {
        if (!matchKeyword("null")) {
            throw invalid("is操作符仅支持null判断");
        }
    }

    private String parseBetweenSecondValue() {
        if (!matchKeyword("and")) {
            throw invalid("between操作符需要两个值");
        }
        return parseValue();
    }

    private RequestCriteriaDto criteria(String attribute, String operator, List<String> values) {
        RequestCriteriaDto criteria = new RequestCriteriaDto();
        criteria.setAttribute(attribute);
        criteria.setOperator(operator);
        criteria.setValueList(values);
        return criteria;
    }

    private WhereNode group(String logic, WhereNode left, WhereNode right) {
        List<WhereNode> children = new ArrayList<>();
        appendGroupChild(children, logic, left);
        appendGroupChild(children, logic, right);
        return WhereNode.group(logic, children);
    }

    private void appendGroupChild(List<WhereNode> children, String logic, WhereNode node) {
        if ("group".equals(node.type()) && logic.equals(node.logic())) {
            children.addAll(node.children());
        } else {
            children.add(node);
        }
    }

    private void collectCriteria(WhereNode node, List<RequestCriteriaDto> criteriaList) {
        if ("condition".equals(node.type())) {
            criteriaList.add(node.criteria());
            return;
        }
        node.children().forEach(child -> collectCriteria(child, criteriaList));
    }

    private List<Token> tokenize(String source) {
        List<Token> result = new ArrayList<>();
        int index = 0;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            char quoteEnd = quoteEnd(current);
            if (quoteEnd != 0) {
                StringBuilder value = new StringBuilder();
                index++;
                boolean closed = false;
                while (index < source.length()) {
                    char quoted = source.charAt(index);
                    if (quoted == quoteEnd) {
                        if ((current == '\'' || current == '"') && index + 1 < source.length() && source.charAt(index + 1) == quoteEnd) {
                            value.append(quoted);
                            index += 2;
                            continue;
                        }
                        index++;
                        closed = true;
                        break;
                    }
                    value.append(quoted);
                    index++;
                }
                if (!closed) {
                    throw invalid("高级where表达式引号不匹配");
                }
                result.add(new Token(TokenType.STRING, value.toString()));
                continue;
            }
            if (current == '(') {
                result.add(new Token(TokenType.LPAREN, "("));
                index++;
                continue;
            }
            if (current == ')') {
                result.add(new Token(TokenType.RPAREN, ")"));
                index++;
                continue;
            }
            if (current == ',') {
                result.add(new Token(TokenType.COMMA, ","));
                index++;
                continue;
            }
            String twoChars = index + 1 < source.length() ? source.substring(index, index + 2) : "";
            if (List.of(">=", "<=", "!=", "<>", "==").contains(twoChars)) {
                result.add(new Token(TokenType.OPERATOR, twoChars));
                index += 2;
                continue;
            }
            if (current == '=' || current == '>' || current == '<') {
                result.add(new Token(TokenType.OPERATOR, Character.toString(current)));
                index++;
                continue;
            }
            int start = index;
            while (index < source.length() && !Character.isWhitespace(source.charAt(index)) && !isDelimiter(source.charAt(index))) {
                index++;
            }
            if (start == index) {
                throw invalid("高级where表达式格式不正确");
            }
            result.add(new Token(TokenType.IDENT, source.substring(start, index)));
        }
        result.add(new Token(TokenType.END, ""));
        return result;
    }

    private boolean isDelimiter(char value) {
        return value == '(' || value == ')' || value == ',' || value == '=' || value == '>' || value == '<' || value == '!';
    }

    private String expectIdentifier(String message) {
        Token token = expect(TokenType.IDENT, message);
        return token.text();
    }

    private String parseValue() {
        Token token = peek();
        if (token.type() != TokenType.STRING && token.type() != TokenType.IDENT) {
            throw invalid("检索条件值不能为空");
        }
        position++;
        String value = token.text().trim();
        if (StringUtils.isBlank(value)) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "检索条件值不能为空");
        }
        if (token.type() == TokenType.IDENT && isUnsafeRawValue(value)) {
            throw invalid("高级where表达式值必须为数字、无空格文本或引号包裹文本");
        }
        return value;
    }

    private boolean isUnsafeRawValue(String value) {
        return StringUtils.containsWhitespace(value)
                || value.contains(";")
                || value.contains("--")
                || value.contains("/*")
                || value.contains("*/");
    }

    private Token expect(TokenType type, String message) {
        Token token = peek();
        if (token.type() != type) {
            throw invalid(message);
        }
        position++;
        return token;
    }

    private boolean match(TokenType type) {
        if (peek().type() == type) {
            position++;
            return true;
        }
        return false;
    }

    private boolean matchKeyword(String keyword) {
        Token token = peek();
        if (token.type() == TokenType.IDENT && keyword.equalsIgnoreCase(token.text())) {
            position++;
            return true;
        }
        return false;
    }

    private Token peek() {
        return tokens.get(position);
    }

    private String normalizeLikeValue(String value) {
        return StringUtils.removeEnd(StringUtils.removeStart(value, "%"), "%");
    }

    private void validateField(String field) {
        if (!FIELD_PATTERN.matcher(field).matches()) {
            throw invalid("高级where表达式字段不合法: " + field);
        }
    }

    private char quoteEnd(char current) {
        return switch (current) {
            case '\'' -> '\'';
            case '"' -> '"';
            case '‘' -> '’';
            case '“' -> '”';
            default -> 0;
        };
    }

    private ApiException invalid(String message) {
        return new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), message);
    }

    record WhereExpression(WhereNode root, String logic, List<RequestCriteriaDto> criteriaList, String normalizedExpression) {
    }

    record WhereNode(String type, String logic, RequestCriteriaDto criteria, List<WhereNode> children) {

        static WhereNode condition(RequestCriteriaDto criteria) {
            return new WhereNode("condition", null, criteria, List.of());
        }

        static WhereNode group(String logic, List<WhereNode> children) {
            return new WhereNode("group", logic, null, children);
        }

        String logicOrDefault() {
            return StringUtils.defaultIfBlank(logic, "and");
        }
    }

    private enum TokenType {
        IDENT,
        STRING,
        OPERATOR,
        LPAREN,
        RPAREN,
        COMMA,
        END
    }

    private record Token(TokenType type, String text) {
    }
}
