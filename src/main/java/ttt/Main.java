package ttt;

import tictactoe.TicTacToe;
import tictactoe.spieler.ILernenderSpieler;
import tictactoe.spieler.ISpieler;
import tictactoe.spieler.beispiel.Zufallsspieler;
import ttt.spieler.NNSpieler;

public class Main {

    private static final double SPIELANZAHL = 1000.0;

    public static void main(final String[] args) {
        final ISpieler spieler1 = new Zufallsspieler("Zufallspieler Tom");
        final ILernenderSpieler spieler2 = new NNSpieler("Norbert Neuronal");

        final TicTacToe spiel = new TicTacToe();

        System.out.println("Vor dem Training");
        System.out.println(spieler2.getName() + " gegen " + spieler1.getName());
        System.out.println("=======================================================");
        fuehreSpieleDurch(spieler1, spieler2, spiel);

        spieler2.trainieren(null);

        System.out.println("Nach dem Training");
        System.out.println(spieler2.getName() + " gegen " + spieler1.getName());
        System.out.println("=======================================================");
        fuehreSpieleDurch(spieler1, spieler2, spiel);

        showDebugSpiele(spieler1, spieler2, spiel);
    }

    public static void showDebugSpiele(ISpieler spieler1, ILernenderSpieler spieler2, TicTacToe spiel) {
        System.out.println("Ein Einzelspiel im DEBUG-Modus, lernender Spieler startet mit X");
        System.out.println("===============================================================");
        System.out
                .println("Gewonnen hat: " + spiel.neuesSpiel(spieler2, spieler1, 150, true).getName());
        System.out.println();
        System.out.println("Ein Einzelspiel im DEBUG-Modus, lernender Spieler zweiter mit O");
        System.out.println("===============================================================");
        System.out
                .println("Gewonnen hat: " + spiel.neuesSpiel(spieler1, spieler2, 150, true).getName());
    }

    private static void fuehreSpieleDurch(ISpieler spieler1, ILernenderSpieler spieler2, TicTacToe spiel) {
        int gewinne1 = 0;
        int gewinne2 = 0;
        ISpieler gewinner;
        for (long i = 0; i < SPIELANZAHL; i++) {
            gewinner = spiel.neuesSpiel(spieler2, spieler1, 150, false);
            if (gewinner == spieler1) {
                gewinne1++;
            } else {
                if (gewinner == spieler2) {
                    gewinne2++;
                }
            }
        }
        System.out.println(
                "Gewinne " + spieler1.getName() + ": " + gewinne1 + ". Gewinne " + spieler2.getName() + ": "
                        + gewinne2);
        System.out.println("Unentschieden: " + (SPIELANZAHL - gewinne1 - gewinne2));
        System.out.println("Winrate schlauer Spieler: " + getWinrate(gewinne2) + "%");
        System.out.println();
    }

    private static double getWinrate(final int anzhalGewinne) {
        return anzhalGewinne * 100.0 / SPIELANZAHL;
    }

}