package by.instruction.papera;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Поиск по тексту документа без тяжёлых regex в WebView.
 * Нормализация: регистр + ё/е. Подсветка — по текстовым узлам.
 */
final class DocumentSearchMatcher {
    static final int MAX_RESULTS = 200;
    static final int MAX_DOM_HIGHLIGHTS = 80;
    private static final int SNIPPET_RADIUS = 42;

    private DocumentSearchMatcher() {
    }

    static String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.trim().replaceAll("\\s+", " ");
    }

    /** Нормализация для сравнения: длина строки сохраняется (важно для индексов). */
    static String normalizeForSearch(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replace('ё', 'е').replace('Ё', 'е');
    }

    static List<DocSearchResult> findMatches(String content, String query,
                                             boolean caseSensitive, boolean wholeWordsOnly) {
        List<DocSearchResult> results = new ArrayList<>();
        String normalizedQuery = normalizeQuery(query);
        if (content == null || content.isEmpty() || normalizedQuery.isEmpty()) {
            return results;
        }

        final String haystack;
        final String needle;
        if (caseSensitive) {
            haystack = content.replace('ё', 'е').replace('Ё', 'Е');
            needle = normalizedQuery.replace('ё', 'е').replace('Ё', 'Е');
        } else {
            haystack = normalizeForSearch(content);
            needle = normalizeForSearch(normalizedQuery);
        }
        if (needle.isEmpty()) {
            return results;
        }

        int from = 0;
        int index = 0;
        while (from <= haystack.length() - needle.length() && index < MAX_RESULTS) {
            int pos = haystack.indexOf(needle, from);
            if (pos < 0) {
                break;
            }
            int end = pos + needle.length();
            if (wholeWordsOnly && !isWholeWord(haystack, pos, end)) {
                from = pos + 1;
                continue;
            }
            // Для фраз: разрешаем совпадение, где в needle пробел, а в тексте перевод строки —
            // indexOf так не умеет, поэтому дополнительно ищем с гибкими пробелами ниже.
            String matchText = content.substring(pos, Math.min(end, content.length()));
            int estimatedPage = Math.max(1,
                    (int) Math.ceil((double) (pos + 1) / Math.max(1, content.length()) * 10));
            results.add(new DocSearchResult(
                    index,
                    matchText,
                    pos,
                    estimatedPage,
                    buildSnippet(content, pos, end)
            ));
            index++;
            from = pos + Math.max(1, needle.length());
        }

        // Если обычный indexOf ничего не нашёл, а в запросе есть пробел — гибкий поиск по токенам
        if (results.isEmpty() && needle.indexOf(' ') >= 0) {
            results.addAll(findMatchesFlexibleWhitespace(content, haystack, needle, wholeWordsOnly));
        }
        return results;
    }

    private static List<DocSearchResult> findMatchesFlexibleWhitespace(
            String original, String haystack, String needle, boolean wholeWordsOnly) {
        List<DocSearchResult> results = new ArrayList<>();
        String[] tokens = needle.split(" ");
        if (tokens.length == 0) {
            return results;
        }
        int from = 0;
        int index = 0;
        while (from < haystack.length() && index < MAX_RESULTS) {
            int start = haystack.indexOf(tokens[0], from);
            if (start < 0) {
                break;
            }
            int cursor = start + tokens[0].length();
            boolean ok = true;
            for (int t = 1; t < tokens.length; t++) {
                int i = cursor;
                while (i < haystack.length() && Character.isWhitespace(haystack.charAt(i))) {
                    i++;
                }
                if (i >= haystack.length() || !haystack.startsWith(tokens[t], i)) {
                    ok = false;
                    break;
                }
                cursor = i + tokens[t].length();
            }
            if (!ok) {
                from = start + 1;
                continue;
            }
            if (wholeWordsOnly && !isWholeWord(haystack, start, cursor)) {
                from = start + 1;
                continue;
            }
            results.add(new DocSearchResult(
                    index,
                    original.substring(start, Math.min(cursor, original.length())),
                    start,
                    Math.max(1, (int) Math.ceil((double) (start + 1) / Math.max(1, original.length()) * 10)),
                    buildSnippet(original, start, cursor)
            ));
            index++;
            from = start + 1;
        }
        return results;
    }

    private static boolean isWholeWord(String text, int start, int end) {
        if (start > 0 && isWordChar(text.charAt(start - 1))) {
            return false;
        }
        if (end < text.length() && isWordChar(text.charAt(end))) {
            return false;
        }
        return true;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    static String buildSnippet(String content, int start, int end) {
        int from = Math.max(0, start - SNIPPET_RADIUS);
        int to = Math.min(content.length(), end + SNIPPET_RADIUS);
        StringBuilder sb = new StringBuilder();
        if (from > 0) {
            sb.append('…');
        }
        sb.append(content.substring(from, to).replaceAll("\\s+", " ").trim());
        if (to < content.length()) {
            sb.append('…');
        }
        return sb.toString();
    }

    /**
     * JS API: принимает обычную строку запроса (не regex).
     * Возвращает true при инициализации — важно для evaluateJavascript.
     */
    static String highlightApiJavaScript() {
        return "(function(){"
                + "if(window.__paperkaSearch&&window.__paperkaSearch.__v===3){return true;}"
                + "function ensureStyle(){"
                + "if(document.getElementById('paperka-search-style')){return;}"
                + "var s=document.createElement('style');"
                + "s.id='paperka-search-style';"
                + "s.textContent='.highlight{background-color:#FFEB3B!important;color:#000!important;"
                + "padding:2px 4px;border-radius:3px;font-weight:bold;display:inline;}"
                + ".highlight.active{background-color:#4CAF50!important;color:#FFF!important;"
                + "padding:3px 6px!important;border-radius:5px!important;border:3px solid #2E7D32!important;"
                + "box-shadow:0 2px 4px rgba(0,0,0,0.3)!important;}';"
                + "document.head.appendChild(s);"
                + "}"
                + "function clearHighlights(){"
                + "var marks=document.querySelectorAll('span.highlight[data-search-idx]');"
                + "for(var i=marks.length-1;i>=0;i--){"
                + "var m=marks[i];var p=m.parentNode;if(!p){continue;}"
                + "while(m.firstChild){p.insertBefore(m.firstChild,m);}"
                + "p.removeChild(m);"
                + "}"
                + "if(document.body&&document.body.normalize){document.body.normalize();}"
                + "}"
                + "function norm(s,caseSensitive){"
                + "if(s==null){return '';}"
                + "var t=caseSensitive?String(s):String(s).toLowerCase();"
                + "return t.replace(/ё/g,'е').replace(/Ё/g, caseSensitive?'Е':'е');"
                + "}"
                + "function isWordChar(ch){"
                + "if(!ch){return false;}"
                + "return /[0-9A-Za-zА-Яа-яЁё_]/.test(ch);"
                + "}"
                + "function isWholeWord(text,start,end){"
                + "if(start>0&&isWordChar(text.charAt(start-1))){return false;}"
                + "if(end<text.length&&isWordChar(text.charAt(end))){return false;}"
                + "return true;"
                + "}"
                + "function collectNodes(){"
                + "var nodes=[];"
                + "if(!document.body){return nodes;}"
                + "var walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,null);"
                + "while(walker.nextNode()){"
                + "var n=walker.currentNode;var p=n.parentElement;"
                + "if(!p){continue;}"
                + "var tag=p.tagName;"
                + "if(tag==='SCRIPT'||tag==='STYLE'||tag==='NOSCRIPT'){continue;}"
                + "if(p.classList&&p.classList.contains('highlight')&&p.hasAttribute('data-search-idx')){continue;}"
                + "if(n.nodeValue&&n.nodeValue.length){nodes.push(n);}"
                + "}"
                + "return nodes;"
                + "}"
                + "function wrapInNode(node,from,to,idx){"
                + "if(!node||!node.parentNode||node.nodeType!==3){return null;}"
                + "var text=node.nodeValue;if(!text){return null;}"
                + "from=Math.max(0,Math.min(from,text.length));"
                + "to=Math.max(from,Math.min(to,text.length));"
                + "if(from===to){return null;}"
                + "var mid=text.substring(from,to);"
                + "var after=text.substring(to);"
                + "node.nodeValue=text.substring(0,from);"
                + "var span=document.createElement('span');"
                + "span.className='highlight';"
                + "span.setAttribute('data-search-idx',String(idx));"
                + "span.textContent=mid;"
                + "var parent=node.parentNode;"
                + "var ref=node.nextSibling;"
                + "parent.insertBefore(span,ref);"
                + "var afterNode=null;"
                + "if(after){afterNode=document.createTextNode(after);parent.insertBefore(afterNode,span.nextSibling);}"
                + "return afterNode;"
                + "}"
                + "window.__paperkaSearch={"
                + "__v:3,"
                + "clear:function(){clearHighlights();},"
                + "highlight:function(query,caseSensitive,maxResults,wholeWords){"
                + "clearHighlights();ensureStyle();"
                + "query=norm(query,caseSensitive).replace(/\\s+/g,' ').trim();"
                + "if(!query){return 0;}"
                + "var tokens=query.split(' ');"
                + "var nodes=collectNodes();"
                + "var count=0;"
                + "function findNext(hay,from){"
                + "if(tokens.length===1){"
                + "var p=hay.indexOf(tokens[0],from);return p<0?null:{start:p,end:p+tokens[0].length};"
                + "}"
                + "var start=hay.indexOf(tokens[0],from);if(start<0){return null;}"
                + "var cursor=start+tokens[0].length;"
                + "for(var t=1;t<tokens.length;t++){"
                + "while(cursor<hay.length&&/\\s/.test(hay.charAt(cursor))){cursor++;}"
                + "if(hay.indexOf(tokens[t],cursor)!==cursor){return findNext(hay,start+1);}"
                + "cursor+=tokens[t].length;"
                + "}"
                + "return {start:start,end:cursor};"
                + "}"
                + "for(var ni=0;ni<nodes.length&&count<maxResults;ni++){"
                + "var node=nodes[ni];"
                + "if(!node||!node.parentNode){continue;}"
                + "var original=node.nodeValue;if(!original){continue;}"
                + "var hay=norm(original,caseSensitive);"
                + "var searchFrom=0;"
                + "while(count<maxResults){"
                + "var hit=findNext(hay,searchFrom);"
                + "if(!hit){break;}"
                + "if(wholeWords&&!isWholeWord(hay,hit.start,hit.end)){searchFrom=hit.start+1;continue;}"
                + "var afterNode=wrapInNode(node,hit.start,hit.end,count);"
                + "count++;"
                + "if(afterNode){"
                + "node=afterNode;"
                + "original=node.nodeValue;"
                + "hay=norm(original,caseSensitive);"
                + "searchFrom=0;"
                + "}else{break;}"
                + "}"
                + "}"
                + "return count;"
                + "},"
                + "activate:function(index){"
                + "var all=document.querySelectorAll('span.highlight[data-search-idx]');"
                + "for(var i=0;i<all.length;i++){all[i].classList.remove('active');}"
                + "var targets=document.querySelectorAll('span.highlight[data-search-idx=\"'+index+'\"]');"
                + "if(!targets.length){return false;}"
                + "for(var j=0;j<targets.length;j++){targets[j].classList.add('active');}"
                + "try{targets[0].scrollIntoView({block:'center',inline:'nearest'});}"
                + "catch(e){targets[0].scrollIntoView(true);}"
                + "return true;"
                + "}"
                + "};"
                + "return true;"
                + "})()";
    }
}
