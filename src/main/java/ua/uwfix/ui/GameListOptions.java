package ua.uwfix.ui;

import ua.uwfix.i18n.I18n;
import ua.uwfix.model.Game;
import ua.uwfix.util.UkrainianCollator;

import java.util.Comparator;
import java.util.Locale;
import java.util.function.Function;

/** Пошук, фільтри і сортування списку ігор. */
final class GameListOptions {

    private GameListOptions() {
    }

    /** Який стан гри показувати. */
    enum Filter {
        ALL("filter.all"),
        FIXED("filter.fixed"),
        NOT_FIXED("filter.notFixed"),
        OUTDATED("filter.outdated");

        private final String key;

        Filter(String key) {
            this.key = key;
        }

        boolean accepts(GameCell.Badge badge) {
            return switch (this) {
                case ALL -> true;
                case FIXED -> badge != GameCell.Badge.NONE;
                case NOT_FIXED -> badge == GameCell.Badge.NONE;
                case OUTDATED -> badge == GameCell.Badge.OUTDATED;
            };
        }

        static Filter parse(String name) {
            for (Filter f : values()) {
                if (f.name().equals(name)) {
                    return f;
                }
            }
            return ALL;
        }

        @Override
        public String toString() {
            return I18n.t(key);
        }
    }

    /** Порядок ігор у списку. */
    enum Sort {
        NAME("sort.name"),
        SOURCE("sort.source"),
        FIXED_FIRST("sort.fixedFirst");

        private final String key;

        Sort(String key) {
            this.key = key;
        }

        Comparator<Game> comparator(Function<Game, GameCell.Badge> badge) {
            Comparator<Game> byName = byName();
            return switch (this) {
                case NAME -> byName;
                case SOURCE -> Comparator.comparing(Game::source).thenComparing(byName);
                case FIXED_FIRST -> Comparator.<Game>comparingInt(g -> rank(badge.apply(g))).thenComparing(byName);
            };
        }

        /** Спершу ті, що потребують уваги (оновились), далі виправлені, потім решта. */
        private static int rank(GameCell.Badge badge) {
            return switch (badge) {
                case OUTDATED -> 0;
                case PATCHED -> 1;
                case NONE -> 2;
            };
        }

        static Sort parse(String name) {
            for (Sort s : values()) {
                if (s.name().equals(name)) {
                    return s;
                }
            }
            return NAME;
        }

        @Override
        public String toString() {
            return I18n.t(key);
        }
    }

    /** Назви порівнюються за українською абеткою (Ґ, Є, І, Ї на своїх місцях). */
    static Comparator<Game> byName() {
        Comparator<String> collator = UkrainianCollator.comparator();
        return (a, b) -> collator.compare(a.name(), b.name());
    }

    /**
     * Пошук не чутливий до регістру, пробілів і розділових знаків:
     * «the wi» знайде «The Witcher 3», «stalker» — «S.T.A.L.K.E.R. 2».
     * Також можна шукати за лаунчером: «epic», «gog».
     */
    static boolean matchesSearch(Game game, String query) {
        String q = normalize(query);
        if (q.isEmpty()) {
            return true;
        }
        return normalize(game.name()).contains(q) || normalize(game.source().displayName()).contains(q);
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : text.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
