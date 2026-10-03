package ua.uwfix.scan;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RegFileParserTest {

    private static final String SAMPLE = "\uFEFFWindows Registry Editor Version 5.00\r\n"
            + "\r\n"
            + "[HKEY_LOCAL_MACHINE\\SOFTWARE\\WOW6432Node\\GOG.com\\Games]\r\n"
            + "\r\n"
            + "[HKEY_LOCAL_MACHINE\\SOFTWARE\\WOW6432Node\\GOG.com\\Games\\1207664643]\r\n"
            + "\"gameName\"=\"The Witcher 3: Wild Hunt\"\r\n"
            + "\"path\"=\"D:\\\\GOG Games\\\\The Witcher 3\"\r\n"
            + "\"dependsOn\"=\"\"\r\n"
            + "\"quoted\"=\"say \\\"hi\\\"\"\r\n"
            + "\"version\"=dword:00000004\r\n"
            + "\"Screenmanager Resolution Width_h182942802\"=dword:00000d70\r\n"
            + "\"blob\"=hex:01,02,03,\\\r\n"
            + "  04,05\r\n"
            + "@=\"default value\"\r\n"
            + "\r\n"
            + "[HKEY_LOCAL_MACHINE\\SOFTWARE\\WOW6432Node\\GOG.com\\Games\\1495134320]\r\n"
            + "\"gameName\"=\"Відьмак 3: Дикий Гін\"\r\n";

    @Test
    void parsesStringValuesOfEveryKey() {
        Map<String, Map<String, String>> keys = RegFileParser.parse(SAMPLE);
        assertEquals(3, keys.size());

        Map<String, String> witcher = keys.get("HKEY_LOCAL_MACHINE\\SOFTWARE\\WOW6432Node\\GOG.com\\Games\\1207664643");
        assertEquals("The Witcher 3: Wild Hunt", witcher.get("gameName"));
        assertEquals("D:\\GOG Games\\The Witcher 3", witcher.get("path"));
        assertEquals("", witcher.get("dependsOn"));
        assertEquals("say \"hi\"", witcher.get("quoted"));
        assertEquals("default value", witcher.get("@"));
        assertEquals("4", witcher.get("version"), "DWORD повертається десятковим рядком");
        assertFalse(witcher.containsKey("blob"), "двійкові значення ігноруються");
        assertEquals("3440", witcher.get("Screenmanager Resolution Width_h182942802"));

        assertEquals("Відьмак 3: Дикий Гін",
                keys.get("HKEY_LOCAL_MACHINE\\SOFTWARE\\WOW6432Node\\GOG.com\\Games\\1495134320").get("gameName"));
    }

    @Test
    void expandsRootAliases() {
        assertEquals("HKEY_LOCAL_MACHINE\\SOFTWARE\\X", WindowsRegistry.expandRoot("HKLM\\SOFTWARE\\X"));
        assertEquals("HKEY_CURRENT_USER\\Software", WindowsRegistry.expandRoot("hkcu\\Software"));
        assertEquals("HKEY_USERS", WindowsRegistry.expandRoot("HKU"));
    }
}
