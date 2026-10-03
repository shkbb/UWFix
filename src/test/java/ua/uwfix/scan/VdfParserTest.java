package ua.uwfix.scan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VdfParserTest {

    @Test
    void parsesModernLibraryFolders() {
        String vdf = """
                "libraryfolders"
                {
                	"0"
                	{
                		"path"		"C:\\\\Program Files (x86)\\\\Steam"
                		"label"		""
                		"apps"
                		{
                			"228980"		"1078046055"
                		}
                	}
                	"1"
                	{
                		"path"		"F:\\\\SteamLibrary"
                	}
                }
                """;
        VdfObject root = VdfParser.parse(vdf);
        VdfObject folders = root.getObject("libraryfolders");
        assertNotNull(folders);
        assertEquals("C:\\Program Files (x86)\\Steam", folders.getObject("0").getString("path"));
        assertEquals("F:\\SteamLibrary", folders.getObject("1").getString("path"));
        assertEquals("1078046055", folders.getObject("0").getObject("apps").getString("228980"));
    }

    @Test
    void parsesAppManifestCaseInsensitively() {
        String acf = """
                "AppState"
                {
                	"appid"		"292030"
                	"name"		"The Witcher 3: Wild Hunt — Remastered"
                	"installdir"		"The Witcher 3"
                }
                """;
        VdfObject app = VdfParser.parse(acf).getObject("appstate");
        assertEquals("292030", app.getString("AppID"));
        assertEquals("The Witcher 3: Wild Hunt — Remastered", app.getString("name"));
        assertEquals("The Witcher 3", app.getString("installdir"));
    }

    @Test
    void handlesEscapesCommentsUnquotedTokensAndConditions() {
        String vdf = """
                // коментар
                root {
                    "quote" "say \\"hi\\""   // коментар після значення
                    unquoted value
                    "tab" "a\\tb"
                    "cond" "x" [$WIN32]
                }
                """;
        VdfObject root = VdfParser.parse(vdf).getObject("root");
        assertEquals("say \"hi\"", root.getString("quote"));
        assertEquals("value", root.getString("unquoted"));
        assertEquals("a\tb", root.getString("tab"));
        assertEquals("x", root.getString("cond"));
        assertNull(root.getString("missing"));
    }

    @Test
    void reportsUnclosedObject() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> VdfParser.parse("\"a\"\n{\n\"b\" \"c\"\n"));
        assertEquals(true, e.getMessage().contains("рядку"));
    }

    @Test
    void reportsUnclosedString() {
        assertThrows(IllegalArgumentException.class, () -> VdfParser.parse("\"a\" \"bc"));
    }
}
