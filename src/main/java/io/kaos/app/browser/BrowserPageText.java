package io.kaos.app.browser;

import com.microsoft.playwright.Page;

/** Extracts a bounded amount of visible text without materializing the whole page text. */
final class BrowserPageText {
    static final int MAX_CODE_POINTS = 4000;
    private static final int MAX_TEXT_NODES = 10000;
    private static final String EXTRACT_BOUNDED_TEXT = """
            element => {
              const maxCodePoints = %d;
              const maxTextNodes = %d;
              const blockTags = "address,article,aside,blockquote,dd,div,dl,dt,fieldset,figcaption,figure,footer,form,h1,h2,h3,h4,h5,h6,header,li,main,nav,ol,p,pre,section,table,td,th,tr,ul";
              const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
              let text = "";
              let codePoints = 0;
              let visitedNodes = 0;
              let lastBlock = null;
              let truncated = false;
              let node;
              while ((node = walker.nextNode()) !== null) {
                if (++visitedNodes > maxTextNodes) {
                  truncated = true;
                  break;
                }
                const parent = node.parentElement;
                if (!parent || /^(SCRIPT|STYLE|TEMPLATE|NOSCRIPT)$/.test(parent.tagName)) continue;
                let current = parent;
                let visible = true;
                while (current && current !== element.parentElement) {
                  const style = window.getComputedStyle(current);
                  if (current.hidden || style.display === "none"
                      || style.visibility === "hidden" || style.visibility === "collapse") {
                    visible = false;
                    break;
                  }
                  current = current.parentElement;
                }
                if (!visible) continue;
                const block = parent.closest(blockTags) || parent;
                if (lastBlock && block !== lastBlock && text.length > 0 && !text.endsWith("\\n")) {
                  if (codePoints === maxCodePoints) {
                    truncated = true;
                    break;
                  }
                  text += "\\n";
                  codePoints++;
                }
                lastBlock = block;
                const value = node.nodeValue || "";
                for (let offset = 0; offset < value.length;) {
                  if (codePoints === maxCodePoints) {
                    truncated = true;
                    break;
                  }
                  const point = value.codePointAt(offset);
                  const width = point > 65535 ? 2 : 1;
                  text += value.slice(offset, offset + width);
                  offset += width;
                  codePoints++;
                }
                if (truncated) break;
              }
              if (text.length > 0 && (visitedNodes > maxTextNodes || codePoints === maxCodePoints)) {
                const nextNode = walker.nextNode();
                if (nextNode !== null) truncated = true;
              }
              return truncated ? text + "\\n[Visible text truncated at the extraction limit.]" : text;
            }
            """.formatted(MAX_CODE_POINTS, MAX_TEXT_NODES);

    private BrowserPageText() {
    }

    static String extract(Page page) {
        Object result = page.locator("body").evaluate(EXTRACT_BOUNDED_TEXT);
        return result == null ? "" : result.toString();
    }
}
