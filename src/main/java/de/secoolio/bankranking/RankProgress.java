package de.secoolio.bankranking;

/**
 * Der Fortschritt eines Spielers innerhalb seines Rangs.
 *
 * <p>Reine Rechnung ohne Server: dieselbe Grundlage fuer die Sidebar-Zeile, den Balken am oberen
 * Bildrand und die Kontoseite.
 *
 * @param rank     der erreichte Rang
 * @param next     der naechste Rang, oder {@code null} auf der hoechsten Stufe
 * @param have     Punkte seit Beginn des aktuellen Rangs
 * @param need     Punkte von einem Rang zum naechsten; 0 auf der hoechsten Stufe
 * @param fraction Anteil zwischen 0 und 1; auf der hoechsten Stufe 1
 */
public record RankProgress(Rank rank, Rank next, double have, double need, double fraction) {

    /** Die Zeichen des Fortschrittsbalkens: gefuellt und offen. */
    public static final String FILLED_COLOR = "<green>";
    public static final String EMPTY_COLOR = "<dark_gray>";

    public static RankProgress of(double points) {
        Rank rank = Rank.of(points);
        int nextAt = rank.nextAt();
        if (nextAt == 0) {
            return new RankProgress(rank, null, 0.0, 0.0, 1.0);
        }
        Rank next = Rank.values()[rank.ordinal() + 1];
        double have = Math.max(0.0, points - rank.from());
        double need = nextAt - rank.from();
        double fraction = need <= 0.0 ? 1.0 : Math.min(1.0, Math.max(0.0, have / need));
        return new RankProgress(rank, next, have, need, fraction);
    }

    /** Wie viele Punkte noch bis zum naechsten Rang fehlen; 0 auf der hoechsten Stufe. */
    public double remaining() {
        return this.next == null ? 0.0 : Math.max(0.0, this.need - this.have);
    }

    /**
     * Der Fortschritt in ganzen Prozent, abgerundet.
     *
     * <p>Abgerundet, damit "100%" nur dann dasteht, wenn der naechste Rang wirklich erreicht ist -
     * bei 999 von 1000 Punkten waere kaufmaennisches Runden irrefuehrend.
     */
    public int percent() {
        return isHighest() ? 100 : (int) Math.floor(this.fraction * 100.0);
    }

    public boolean isHighest() {
        return this.next == null;
    }

    /**
     * Der Balken als MiniMessage-Text.
     *
     * @param width  Anzahl der Zeichen
     * @param symbol das Zeichen selbst; der senkrechte Strich ist in jeder Schrift vorhanden
     */
    public String bar(int width, String symbol) {
        int safeWidth = Math.max(1, Math.min(64, width));
        // Abgerundet wie percent(): ein voller Balken steht nur bei wirklich erreichtem Rang.
        int filled = (int) Math.floor(this.fraction * safeWidth);
        StringBuilder sb = new StringBuilder(FILLED_COLOR);
        sb.append(symbol.repeat(filled));
        sb.append(EMPTY_COLOR);
        sb.append(symbol.repeat(safeWidth - filled));
        return sb.toString();
    }
}
