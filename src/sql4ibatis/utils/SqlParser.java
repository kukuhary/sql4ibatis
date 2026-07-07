package sql4ibatis.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser utility class for analyzing XML query texts, extracting parameters, and evaluating MyBatis dynamic SQL <if> expressions.
 */
public class SqlParser {

	private static class VarOccurrence implements Comparable<VarOccurrence> {
		final String name;
		final int index;

		VarOccurrence(String name, int index) {
			this.name = name;
			this.index = index;
		}

		@Override
		public int compareTo(VarOccurrence o) {
			return Integer.compare(this.index, o.index);
		}
	}

	/**
	 * Resolves <include refid="..." /> tags by replacing them with the corresponding <sql id="..."> fragment from the XML text.
	 */
	public static String resolveIncludes(String sql, String xmlText, Map<String, String> xmlContentsMap) {
		if (sql == null || xmlText == null) {
			return sql;
		}

		String resolved = sql;
		Pattern includePattern = Pattern.compile("<include\\s+refid\\s*=\\s*(?:[']([^']*)[']|[\"]([^\"]*)[\"])\\s*(?:/>|></include>)");
		
		boolean found;
		int loopCount = 0;
		do {
			found = false;
			Matcher m = includePattern.matcher(resolved);
			StringBuffer sb = new StringBuffer();
			while (m.find()) {
				String refId = m.group(1) != null ? m.group(1) : m.group(2);
				String sqlFragment = findSqlFragment(refId, xmlText, xmlContentsMap);
				
				if (sqlFragment != null) {
					// Mark the start and end of the resolved include block with comments
					String markedFragment = "\n/* -- INCLUDE START: " + refId + " -- */\n" 
							+ sqlFragment 
							+ "\n/* -- INCLUDE END: " + refId + " -- */\n";
					m.appendReplacement(sb, Matcher.quoteReplacement(markedFragment));
					found = true;
				} else {
					m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
				}
			}
			m.appendTail(sb);
			resolved = sb.toString();
			loopCount++;
		} while (found && loopCount < 5);

		return resolved;
	}

	private static String findSqlFragment(String refId, String xmlText, Map<String, String> xmlContentsMap) {
		String fragment = extractSqlTagContent(refId, xmlText);
		if (fragment != null) {
			return fragment;
		}

		if (refId.contains(".")) {
			int lastDot = refId.lastIndexOf(".");
			String namespace = refId.substring(0, lastDot);
			String shortId = refId.substring(lastDot + 1);

			fragment = extractSqlTagContent(shortId, xmlText);
			if (fragment != null) {
				return fragment;
			}

			if (xmlContentsMap != null && xmlContentsMap.containsKey(namespace)) {
				String targetXmlText = xmlContentsMap.get(namespace);
				fragment = extractSqlTagContent(shortId, targetXmlText);
				if (fragment != null) {
					return fragment;
				}
				fragment = extractSqlTagContent(refId, targetXmlText);
				if (fragment != null) {
					return fragment;
				}
			}
		}

		return null;
	}

	private static String extractSqlTagContent(String id, String xmlText) {
		Pattern sqlPattern = Pattern.compile("<sql\\s+id\\s*=\\s*(?:[']" + Pattern.quote(id) + "[']|[\"]" + Pattern.quote(id) + "[\"])\\s*>([\\s\\S]*?)</sql>");
		Matcher m = sqlPattern.matcher(xmlText);
		if (m.find()) {
			String content = m.group(1);
			if (content.contains("<![CDATA[")) {
				content = content.replace("<![CDATA[", "").replace("]]>", "");
			}
			return content.trim();
		}
		return null;
	}
	private static boolean isTargetTag(String sub, String tagName) {
		if (!sub.startsWith("<" + tagName)) {
			return false;
		}
		int len = tagName.length() + 1; // Length of "<" + tagName
		if (sub.length() <= len) {
			return true;
		}
		char nextChar = sub.charAt(len);
		return Character.isWhitespace(nextChar) || nextChar == '>';
	}

	/**
	 * Extracts raw SQL statements by backtracking query tags from the current cursor offset in XML text.
	 */
	public static String extractQueryAtCursor(String xmlText, int offset) {
		int startTagPos = -1;
		String tagType = null;

		for (int i = offset; i >= 0; i--) {
			if (i < xmlText.length() && xmlText.charAt(i) == '<') {
				String sub = xmlText.substring(i);
				if (isTargetTag(sub, "select") || isTargetTag(sub, "insert") || isTargetTag(sub, "update") || isTargetTag(sub, "delete") || isTargetTag(sub, "sql")) {
					startTagPos = i;
					if (isTargetTag(sub, "select")) tagType = "select";
					else if (isTargetTag(sub, "insert")) tagType = "insert";
					else if (isTargetTag(sub, "update")) tagType = "update";
					else if (isTargetTag(sub, "delete")) tagType = "delete";
					else if (isTargetTag(sub, "sql")) tagType = "sql";
					break;
				}
			}
		}

		if (startTagPos == -1 || tagType == null) {
			return null;
		}

		String closingTag = "</" + tagType + ">";
		int endTagPos = xmlText.indexOf(closingTag, startTagPos);
		if (endTagPos == -1) {
			return null;
		}

		int endOfTagPos = endTagPos + closingTag.length();

		if (offset < startTagPos || offset > endOfTagPos) {
			return null;
		}

		String tagContent = xmlText.substring(startTagPos, endOfTagPos);
		String sql = tagContent;
		// 1. Strip the outer query tags (<select ...> and </select>)
		int firstCloseBracket = sql.indexOf(">") + 1;
		int lastOpenBracket = sql.lastIndexOf("<");
		if (lastOpenBracket > firstCloseBracket) {
			sql = sql.substring(firstCloseBracket, lastOpenBracket);
		}

		// 2. Unwrap all CDATA sections inside the query
		if (sql.contains("<![CDATA[")) {
			sql = sql.replace("<![CDATA[", "").replace("]]>", "");
		}

		return sql.trim();
	}

	/**
	 * Parses the 'id' attribute of the query tag containing the cursor offset.
	 */
	public static String extractQueryId(String xmlText, int offset) {
		int startTagPos = -1;
		String tagType = null;

		for (int i = offset; i >= 0; i--) {
			if (i < xmlText.length() && xmlText.charAt(i) == '<') {
				String sub = xmlText.substring(i);
				if (isTargetTag(sub, "select") || isTargetTag(sub, "insert") || isTargetTag(sub, "update") || isTargetTag(sub, "delete") || isTargetTag(sub, "sql")) {
					startTagPos = i;
					if (isTargetTag(sub, "select")) tagType = "select";
					else if (isTargetTag(sub, "insert")) tagType = "insert";
					else if (isTargetTag(sub, "update")) tagType = "update";
					else if (isTargetTag(sub, "delete")) tagType = "delete";
					else if (isTargetTag(sub, "sql")) tagType = "sql";
					break;
				}
			}
		}

		if (startTagPos == -1 || tagType == null) {
			return "";
		}

		String closingTag = "</" + tagType + ">";
		int endTagPos = xmlText.indexOf(closingTag, startTagPos);
		if (endTagPos == -1) {
			return "";
		}

		int endOfTagPos = endTagPos + closingTag.length();

		if (offset < startTagPos || offset > endOfTagPos) {
			return "";
		}

		int closingBracket = xmlText.indexOf(">", startTagPos);
		if (closingBracket == -1) {
			return "";
		}

		String openingTagText = xmlText.substring(startTagPos, closingBracket + 1);

		Pattern idPattern = Pattern.compile("id\\s*=\\s*\"([^\"]+)\"");
		Matcher matcher = idPattern.matcher(openingTagText);
		if (matcher.find()) {
			return matcher.group(1);
		}

		Pattern idSinglePattern = Pattern.compile("id\\s*=\\s*'([^']+)'");
		Matcher singleMatcher = idSinglePattern.matcher(openingTagText);
		if (singleMatcher.find()) {
			return singleMatcher.group(1);
		}

		return "(Unknown ID)";
	}

	/**
	 * Scans and extracts parameter patterns from the SQL body and returns them in physical occurrence order.
	 */
	public static List<String> extractParameters(String sql) {
		List<VarOccurrence> occurrences = new ArrayList<>();

		// 1. Scan MyBatis <if test="..."> tags and extract variables from test expressions (supports quotes mixing)
		Pattern ifPattern = Pattern.compile("<if\\s+test\\s*=\\s*(?:[']([^']*)[']|[\"]([^\"]*)[\"])\\s*>");
		Matcher ifMatcher = ifPattern.matcher(sql);
		while (ifMatcher.find()) {
			String testExpr = ifMatcher.group(1) != null ? ifMatcher.group(1) : ifMatcher.group(2);
			int startPos = ifMatcher.start();
			Set<String> ifVars = extractIfVariables(testExpr);
			for (String var : ifVars) {
				occurrences.add(new VarOccurrence("(if) " + var, startPos));
			}
		}

		// 1.5. Scan MyBatis <when test="..."> tags inside <choose> and extract variables from test expressions
		Pattern whenPattern = Pattern.compile("<when\\s+test\\s*=\\s*(?:[']([^']*)[']|[\"]([^\"]*)[\"])\\s*>");
		Matcher whenMatcher = whenPattern.matcher(sql);
		while (whenMatcher.find()) {
			String testExpr = whenMatcher.group(1) != null ? whenMatcher.group(1) : whenMatcher.group(2);
			int startPos = whenMatcher.start();
			Set<String> whenVars = extractIfVariables(testExpr);
			for (String var : whenVars) {
				occurrences.add(new VarOccurrence("(if) " + var, startPos));
			}
		}

		// 2. Scan iBATIS #variable# patterns
		Pattern ibatisHashPattern = Pattern.compile("#([^#\\s]+)#");
		Matcher ibatisHashMatcher = ibatisHashPattern.matcher(sql);
		while (ibatisHashMatcher.find()) {
			occurrences.add(new VarOccurrence(ibatisHashMatcher.group(1), ibatisHashMatcher.start()));
		}

		// 3. Scan iBATIS $variable$ patterns
		Pattern ibatisDollarPattern = Pattern.compile("\\$([^\\$\\s]+)\\$");
		Matcher ibatisDollarMatcher = ibatisDollarPattern.matcher(sql);
		while (ibatisDollarMatcher.find()) {
			occurrences.add(new VarOccurrence(ibatisDollarMatcher.group(1), ibatisDollarMatcher.start()));
		}

		// 4. Scan MyBatis #{variable} patterns
		Pattern mybatisHashPattern = Pattern.compile("#\\{([^}]+)\\}");
		Matcher mybatisHashMatcher = mybatisHashPattern.matcher(sql);
		while (mybatisHashMatcher.find()) {
			occurrences.add(new VarOccurrence(mybatisHashMatcher.group(1).trim(), mybatisHashMatcher.start()));
		}

		// 5. Scan MyBatis ${variable} patterns
		Pattern mybatisDollarPattern = Pattern.compile("\\$\\{([^}]+)\\}");
		Matcher mybatisDollarMatcher = mybatisDollarPattern.matcher(sql);
		while (mybatisDollarMatcher.find()) {
			occurrences.add(new VarOccurrence(mybatisDollarMatcher.group(1).trim(), mybatisDollarMatcher.start()));
		}

		// Sort occurrences by their physical indices
		Collections.sort(occurrences);

		List<String> finalParams = new ArrayList<>();
		Set<String> added = new HashSet<>();
		for (VarOccurrence occ : occurrences) {
			if (!added.contains(occ.name)) {
				finalParams.add(occ.name);
				added.add(occ.name);
			}
		}

		return finalParams;
	}

	/**
	 * Extracts pure variable names from <if test="..."> expressions.
	 */
	private static Set<String> extractIfVariables(String testExpression) {
		Set<String> vars = new HashSet<>();
		String cleanExpr = testExpression.replaceAll("\"[^\"]*\"", "").replaceAll("'[^']*'", "");
		
		Pattern p = Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]*\\b");
		Matcher m = p.matcher(cleanExpr);
		
		Set<String> excludes = new HashSet<>(Arrays.asList(
			"null", "and", "or", "not", "true", "false", "eq", "ne", "lt", "gt", "isEmpty", "and", "AND", "or", "OR"
		));
		
		while (m.find()) {
			String var = m.group();
			if (!excludes.contains(var) && !var.matches("\\d+")) {
				vars.add(var);
			}
		}
		return vars;
	}

	/**
	 * Evaluates <if test="..."> tags based on user inputs and preserves true blocks.
	 */
	public static String parseDynamicSql(String sql, Map<String, String> values) {
		if (values == null) {
			return sql;
		}

		Map<String, String> baseValues = new HashMap<>();
		for (Map.Entry<String, String> entry : values.entrySet()) {
			String cleanKey = entry.getKey().replace("(if) ", "").trim();
			baseValues.put(cleanKey, entry.getValue());
		}

		Pattern p = Pattern.compile("<if\\s+test\\s*=\\s*(?:[']([^']*)[']|[\"]([^\"]*)[\"])\\s*>([\\s\\S]*?)</if>");
		Matcher m = p.matcher(sql);
		
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			String testExpr = m.group(1) != null ? m.group(1) : m.group(2);
			String innerSql = m.group(3);
			
			boolean isTrue = evaluateCondition(testExpr, baseValues);
			if (isTrue) {
				m.appendReplacement(sb, Matcher.quoteReplacement(innerSql));
			} else {
				m.appendReplacement(sb, "");
			}
		}
		m.appendTail(sb);
		return sb.toString();
	}

	/**
	 * Evaluates MyBatis <if test="..."> conditions.
	 */
	private static boolean evaluateCondition(String testExpr, Map<String, String> values) {
		String[] andParts = testExpr.split("\\s+(?i)and\\s+");
		boolean finalResult = true;

		for (String part : andParts) {
			part = part.trim();
			if (part.isEmpty()) continue;
			
			boolean partResult = evaluateSingleCondition(part, values);
			finalResult = finalResult && partResult;
		}

		return finalResult;
	}

	/**
	 * Evaluates a single condition expression.
	 */
	private static boolean evaluateSingleCondition(String part, Map<String, String> values) {
		if (part.contains("!= null") || part.contains("!=null")) {
			String varName = part.split("!=")[0].trim();
			String val = values.get(varName);
			return val != null && !val.isEmpty();
		}
		
		if (part.contains("== null") || part.contains("==null")) {
			String varName = part.split("==")[0].trim();
			String val = values.get(varName);
			return val == null || val.isEmpty();
		}
		
		if (part.startsWith("!") && part.contains(".isEmpty()")) {
			String varName = part.substring(1, part.indexOf(".isEmpty()")).trim();
			String val = values.get(varName);
			return val != null && !val.isEmpty();
		}
		
		if (!part.startsWith("!") && part.contains(".isEmpty()")) {
			String varName = part.substring(0, part.indexOf(".isEmpty()")).trim();
			String val = values.get(varName);
			return val == null || val.isEmpty();
		}
		
		if (part.contains("==")) {
			String[] split = part.split("==");
			String varName = split[0].trim();
			String compareVal = split[1].trim().replace("\"", "").replace("'", "");
			String actualVal = values.get(varName);
			return compareVal.equals(actualVal);
		}
		
		if (part.contains("!=")) {
			String[] split = part.split("!=");
			String varName = split[0].trim();
			String compareVal = split[1].trim().replace("\"", "").replace("'", "");
			String actualVal = values.get(varName);
			return !compareVal.equals(actualVal);
		}
		
		return true;
	}

	/**
	 * Replaces variables in the SQL query with user input parameter values.
	 */
	public static String resolveParameters(String sql, Map<String, String> values) {
		if (values == null || values.isEmpty()) {
			return sql;
		}

		String resolvedSql = sql;
		for (Map.Entry<String, String> entry : values.entrySet()) {
			if (entry.getKey().startsWith("(if) ")) {
				continue;
			}

			String key = entry.getKey();
			String val = entry.getValue();

			boolean isNumeric = val.matches("-?\\d+(\\.\\d+)?");
			String replacementForHash = isNumeric ? val : "'" + val + "'";

			resolvedSql = resolvedSql.replace("#" + key + "#", replacementForHash);
			resolvedSql = resolvedSql.replace("#{" + key + "}", replacementForHash);
			
			resolvedSql = resolvedSql.replace("$" + key + "$", val);
			resolvedSql = resolvedSql.replace("${" + key + "}", val);
		}

		return resolvedSql;
	}

	/**
	 * Evaluates MyBatis <choose>/<when>/<otherwise> tags based on user inputs and preserves the matching branch.
	 */
	public static String parseChooseSql(String sql, Map<String, String> values) {
		if (values == null) {
			return sql;
		}

		Map<String, String> baseValues = new HashMap<>();
		for (Map.Entry<String, String> entry : values.entrySet()) {
			String cleanKey = entry.getKey().replace("(if) ", "").trim();
			baseValues.put(cleanKey, entry.getValue());
		}

		Pattern choosePattern = Pattern.compile("<choose>([\\s\\S]*?)</choose>");
		Matcher m = choosePattern.matcher(sql);
		
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			String chooseBody = m.group(1);
			String selectedBranch = evaluateChooseBody(chooseBody, baseValues);
			m.appendReplacement(sb, Matcher.quoteReplacement(selectedBranch));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	private static String evaluateChooseBody(String chooseBody, Map<String, String> values) {
		Pattern whenPattern = Pattern.compile("<when\\s+test\\s*=\\s*(?:[']([^']*)[']|[\"]([^\"]*)[\"])\\s*>([\\s\\S]*?)</when>");
		Matcher m = whenPattern.matcher(chooseBody);
		
		while (m.find()) {
			String testExpr = m.group(1) != null ? m.group(1) : m.group(2);
			String branchBody = m.group(3);
			
			boolean isTrue = evaluateCondition(testExpr, values);
			if (isTrue) {
				return branchBody;
			}
		}

		Pattern otherwisePattern = Pattern.compile("<otherwise>([\\s\\S]*?)</otherwise>");
		Matcher mOther = otherwisePattern.matcher(chooseBody);
		if (mOther.find()) {
			return mOther.group(1);
		}

		return "";
	}

	/**
	 * Unwraps MyBatis <foreach> tags and maps the item variable to the collection variable.
	 */
	public static String unwrapForeach(String sql) {
		Pattern foreachPattern = Pattern.compile("<foreach\\s+collection\\s*=\\s*(?:[']([^']*)[']|[\"]([^\"]*)[\"])[\\s\\S]*?>([\\s\\S]*?)</foreach>");
		Matcher m = foreachPattern.matcher(sql);
		
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			String collectionExpr = m.group(1) != null ? m.group(1) : m.group(2);
			String innerBody = m.group(3);
			
			String baseVar = collectionExpr;
			if (baseVar.contains(".")) {
				baseVar = baseVar.substring(0, baseVar.indexOf("."));
			}
			baseVar = baseVar.trim();
			
			String unwrapped = innerBody.replaceAll("#\\{\\s*item\\s*\\}", "#{" + baseVar + "}");
			unwrapped = unwrapped.replaceAll("\\$\\{\\s*item\\s*\\}", "${" + baseVar + "}");
			
			m.appendReplacement(sb, Matcher.quoteReplacement(unwrapped));
		}
		m.appendTail(sb);
		return sb.toString();
	}
}
