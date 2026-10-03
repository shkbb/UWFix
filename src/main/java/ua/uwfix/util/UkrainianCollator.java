package ua.uwfix.util;

import java.text.Collator;
import java.text.ParseException;
import java.text.RuleBasedCollator;
import java.util.Comparator;
import java.util.Locale;

/**
 * Порівняння рядків за українською абеткою.
 * <p>
 * Стандартні правила Java (Unicode) вважають Ґ варіантом Г, а Ї — варіантом І з діакритикою,
 * тож «Ґазда» опиняється перед «Гра». В українській абетці Ґ, Є, І, Ї — окремі літери:
 * А Б В Г Ґ Д Е Є Ж З И І Ї Й … Тому до правил додаються уточнення:
 * {@code & г < ґ & е < є & и < і < ї}.
 */
public final class UkrainianCollator {

    private static final String TAILORING =
            "& г, Г < ґ, Ґ & е, Е < є, Є & и, И < і, І < ї, Ї";

    private UkrainianCollator() {
    }

    /** Порівнювач без урахування регістру (Аа однакові), але з урахуванням літер абетки. */
    public static Comparator<String> comparator() {
        Collator collator;
        try {
            RuleBasedCollator base = (RuleBasedCollator) Collator.getInstance(Locale.forLanguageTag("uk"));
            collator = new RuleBasedCollator(base.getRules() + TAILORING);
        } catch (ParseException | ClassCastException e) {
            collator = Collator.getInstance(Locale.forLanguageTag("uk"));
        }
        collator.setStrength(Collator.SECONDARY);
        Collator result = collator;
        return result::compare;
    }
}
