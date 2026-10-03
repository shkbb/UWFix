package ua.uwfix.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import ua.uwfix.analysis.BinaryCandidate;
import ua.uwfix.patch.PatchRecord;
import ua.uwfix.patch.Patcher.FileState;

/** Рядок таблиці файлів: файл гри, його стан і позначка «патчити». */
public final class FileRow {

    private final BinaryCandidate candidate;
    private final FileState state;
    private final PatchRecord record;
    private final BooleanProperty selected;

    public FileRow(BinaryCandidate candidate, FileState state, PatchRecord record) {
        this.candidate = candidate;
        this.state = state;
        this.record = record;
        this.selected = new SimpleBooleanProperty(candidate.recommended());
    }

    public BinaryCandidate candidate() {
        return candidate;
    }

    public FileState state() {
        return state;
    }

    /** Запис про патч або {@code null}, якщо файл не змінювався. */
    public PatchRecord record() {
        return record;
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }
}
