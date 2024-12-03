package ttt.spieler;


import tictactoe.Spielfeld;
import tictactoe.Zug;

public class SpielHistoryEntry {

    public SpielHistoryEntry(Spielfeld spielfeld, Zug zug) {

        this.spielfeld = spielfeld;
        this.zug = zug;
    }

    private final Spielfeld spielfeld;
    private final Zug zug;

    public Spielfeld getSpielfeld() {
        return spielfeld;
    }

    public Zug getZug() {
        return zug;
    }
}
