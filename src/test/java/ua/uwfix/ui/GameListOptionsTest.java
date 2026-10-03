package ua.uwfix.ui;

import org.junit.jupiter.api.Test;
import ua.uwfix.model.Game;
import ua.uwfix.model.GameSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameListOptionsTest {

    private static Game game(String name, GameSource source) {
        // назва гри може містити «:», а в шляхах Windows він заборонений
        return new Game(source, name, name, Path.of("C:\\Games", "game" + Math.abs(name.hashCode())));
    }

    @Test
    void searchIgnoresCaseSpacesAndPunctuation() {
        Game witcher = game("The Witcher 3: Wild Hunt — Remastered", GameSource.STEAM);
        assertTrue(GameListOptions.matchesSearch(witcher, "the wi"));
        assertTrue(GameListOptions.matchesSearch(witcher, "WITCHER 3"));
        assertTrue(GameListOptions.matchesSearch(witcher, "witcher3"));
        assertTrue(GameListOptions.matchesSearch(witcher, ""));
        assertTrue(GameListOptions.matchesSearch(witcher, null));
        assertFalse(GameListOptions.matchesSearch(witcher, "cyberpunk"));
        assertTrue(GameListOptions.matchesSearch(game("S.T.A.L.K.E.R. 2", GameSource.STEAM), "stalker"));
        assertTrue(GameListOptions.matchesSearch(game("Hogwarts Legacy", GameSource.EPIC), "epic"), "пошук за лаунчером");
    }

    @Test
    void filtersByFixState() {
        assertTrue(GameListOptions.Filter.ALL.accepts(GameCell.Badge.NONE));
        assertTrue(GameListOptions.Filter.FIXED.accepts(GameCell.Badge.PATCHED));
        assertTrue(GameListOptions.Filter.FIXED.accepts(GameCell.Badge.OUTDATED));
        assertFalse(GameListOptions.Filter.FIXED.accepts(GameCell.Badge.NONE));
        assertTrue(GameListOptions.Filter.NOT_FIXED.accepts(GameCell.Badge.NONE));
        assertFalse(GameListOptions.Filter.OUTDATED.accepts(GameCell.Badge.PATCHED));
        assertEquals(GameListOptions.Filter.ALL, GameListOptions.Filter.parse(null));
        assertEquals(GameListOptions.Sort.SOURCE, GameListOptions.Sort.parse("SOURCE"));
    }

    @Test
    void sortsByNameSourceAndFixState() {
        Game witcher = game("The Witcher 3", GameSource.STEAM);
        Game alan = game("Alan Wake 2", GameSource.EPIC);
        Game baldur = game("Baldur's Gate 3", GameSource.GOG);
        Game lis = game("Life is Strange", GameSource.STEAM);
        Map<String, GameCell.Badge> badges = Map.of(
                witcher.id(), GameCell.Badge.PATCHED,
                lis.id(), GameCell.Badge.OUTDATED);

        List<Game> list = new ArrayList<>(List.of(witcher, alan, baldur, lis));
        list.sort(GameListOptions.Sort.NAME.comparator(g -> GameCell.Badge.NONE));
        assertEquals(List.of(alan, baldur, lis, witcher), list);

        list.sort(GameListOptions.Sort.SOURCE.comparator(g -> GameCell.Badge.NONE));
        assertEquals(List.of(lis, witcher, alan, baldur), list, "Steam, Epic, GOG — у порядку лаунчерів");

        list.sort(GameListOptions.Sort.FIXED_FIRST.comparator(g -> badges.getOrDefault(g.id(), GameCell.Badge.NONE)));
        assertEquals(List.of(lis, witcher, alan, baldur), list, "спершу оновлені, потім виправлені, далі за назвою");
    }

    @Test
    void ukrainianNamesSortAlphabetically() {
        List<Game> list = new ArrayList<>(List.of(game("Їжак", GameSource.MANUAL), game("Ґазда", GameSource.MANUAL),
                game("Гра", GameSource.MANUAL), game("Ірій", GameSource.MANUAL)));
        list.sort(GameListOptions.byName());
        assertEquals(List.of("Гра", "Ґазда", "Ірій", "Їжак"), list.stream().map(Game::name).toList());
    }
}
