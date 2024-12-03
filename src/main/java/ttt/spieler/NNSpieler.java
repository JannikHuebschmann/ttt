package ttt.spieler;

import org.apache.commons.lang3.ArrayUtils;
import org.neuroph.core.NeuralNetwork;
import org.neuroph.core.data.DataSet;
import org.neuroph.core.data.DataSetRow;
import org.neuroph.core.exceptions.NeurophException;
import org.neuroph.nnet.MultiLayerPerceptron;
import org.neuroph.util.TransferFunctionType;
import tictactoe.Farbe;
import tictactoe.Spielfeld;
import tictactoe.TicTacToe;
import tictactoe.Zug;
import tictactoe.spieler.IAbbruchbedingung;
import tictactoe.spieler.ILernenderSpieler;
import tictactoe.spieler.ISpieler;
import tictactoe.spieler.beispiel.Zufallsspieler;


import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class NNSpieler implements ILernenderSpieler {

    //https://medium.com/@carsten.friedrich/part-4-neural-network-q-learning-a-tic-tac-toe-player-that-learns-kind-of-2090ca4798d
// input 27 bits, 9x kreuz, 9x kreis. 9x empty, true = belegt
// output 9 bits, value = kreuz, kreis, empty
    private Spielfeld spielfeld;
    private String meinName;
    private Farbe meineFarbe;
    private MultiLayerPerceptron mlp;
    private boolean training;

    List<SpielHistoryEntry> spielHistory;

    // Als Class-Variable, um schnelle Anpassungen zu ermöglichen
    final double lernrate = 0.5d;
    final double discount = 0.75d;
    double explorationRate = 1.0d;
    private final List<Integer> neuronsInLayers = Arrays.asList(27,9 ,9,9, 9);
    private final TransferFunctionType transferFunctionType = TransferFunctionType.SIGMOID;

    //150k ist gut
    final int iterations = 1000;
    double explorationDelta = explorationRate / iterations;
    private final String safeNumber = "0";


    public NNSpieler(final String meinName) {
        this.meinName = meinName;
        try {
            // Auf bereits ausgeführten Runden aufbauen
            mlp = (MultiLayerPerceptron) NeuralNetwork.createFromFile("neuronalesNetz.nnet");
        } catch (NeurophException e) {
            mlp = new MultiLayerPerceptron(neuronsInLayers, transferFunctionType);
        }
    }

    public boolean trainieren(final IAbbruchbedingung paramIAbbruchbedingung) {
        training = true;

        final TicTacToe spiel = new TicTacToe();

        final Zufallsspieler zs = new Zufallsspieler("Zufallsmann Zappalappa");

        for (int episode = 0; episode < iterations; episode++) {

            if (episode % 100 == 0) {
                System.out.println("Durchlauf: " + episode);
            }

            // Für jedes Spiel die History überschreiben, weil wir die alte schon benutzt haben
            spielHistory = new LinkedList<>();

            // Gemischt trainieren
            ISpieler gewinner;

            if (episode % 2 == 0) {
                gewinner = spiel.neuesSpiel(this, zs, 1, false);
            } else {
                gewinner = spiel.neuesSpiel(zs, this, 1, false);
            }

            // Wir lernen nur nach jedem Spiel, nicht nach jedem Zug
            boolean belohnung = gewinner != null && gewinner.equals(this);

            updateNNSpieler(belohnung);
        }

        // training beenden
        training = false;
//    mlp.save("neuronalesNetz.nnet");
        mlp.save("vorhandeneNetzwerke/nn" + neuronsInLayers.toString().strip() + transferFunctionType + "iter" + iterations + "n" + safeNumber + ".nnet");

        return true;
    }

    @Override
    public void speichereWissen(String s) throws IOException {

    }

    @Override
    public void ladeWissen(String s) throws IOException {

    }

    private void updateNNSpieler(final boolean belohnung) {
        // Vorliegende Informationen: Verlauf des Spiels und Belohnung

        // Hier drin eine DataRow für jeden Zug speichern, erwartetes Ergebnis mit Belohnung und Discount anpassen
        final DataSet dataSet = new DataSet(27, 9);

        // DataSet befüllen
        for (int i = 0; i < spielHistory.size(); i++) {
            final SpielHistoryEntry spielHistoryEntry = spielHistory.get(i);
            final DataSetRow row = new DataSetRow();

            // Input ist die eigentliche Ausgangssituation
            row.setInput(convertToBinary(erzeugeInputVektor(spielHistoryEntry.getSpielfeld())));

            // ExpectedOutput ist jedoch an der Stelle des eigentlichen Zuges manipuliert

            // QValues für Ausgangssituation erzeugen
            final double belohnungDouble = belohnung ? 1.0d : 0.0d;
            final double[] expectedOutput = berechneQValue(spielHistoryEntry.getSpielfeld());
            final int choosenIndex = getIndexForZug(spielHistoryEntry.getZug());
            double biggestNachfolgerQValue = 0.0d;

            // Wenn es noch einen Nachfolger gibt
            if (i < spielHistory.size() - 1) {
                final double[] nextQValues = berechneQValue(spielHistory.get(i + 1).getSpielfeld());
                final int indexMaxQValue = getIndexMaxQValue(nextQValues);
                biggestNachfolgerQValue = nextQValues[indexMaxQValue];
            }

            // Der gewählte Zug ist tatsächlich so viel Wert, wie die dafür erhaltene Belohnung und der größte,
            // abdiskontierte Q-Value des nachfolgenden Zustandes, falls dieser existent ist
            if (i == spielHistory.size() - 1) {
                expectedOutput[choosenIndex] =
                        lernrate * (belohnungDouble + (discount * biggestNachfolgerQValue));
            } else {
                expectedOutput[choosenIndex] = lernrate * (discount * biggestNachfolgerQValue);
            }

            // Für Felder, in denen bereits ein Symbol ist, soll das Netzwerk 0.0 antworten
            for (int x = 0; x <= 2; x++) {
                for (int y = 0; y <= 2; y++) {
                    final Farbe farbe = spielHistoryEntry.getSpielfeld().getFarbe(x, y);
                    if (farbe != Farbe.Leer) {
                        final int indexForZug = getIndexForZug(new Zug(x, y));
                        expectedOutput[indexForZug] = 0.0d;
                    }
                }
            }

            row.setDesiredOutput(Arrays.copyOf(expectedOutput, 9));
            dataSet.add(row);
        }

        mlp.learn(dataSet);

        // Explorationsrate verringern
        explorationRate -= explorationDelta;
    }

    private double[] convertToBinary(double[] normalInput) {
        double[] binaryInput = new double[27];
        Arrays.fill(binaryInput, 0.0);
        // Erste 9 sind Leer, zweite 9 sind Kreuz, dritte 9 sind Kreis
        for (int i = 0; i < normalInput.length; i++) {
            double value = normalInput[i];
            if (value == 0) {
                binaryInput[i] = 1;
            } else if (value == 1) {
                binaryInput[i + 9] = 1;
            } else if (value == -1) {
                binaryInput[i + 18] = 1;
            }
        }
        return binaryInput;
    }


    private Zug berechneLernZug(final Zug vorherigerZug) {
        // Spielfeld aktualisieren
        if (vorherigerZug != null) {
            spielfeld.setFarbe(vorherigerZug.getZeile(),
                    vorherigerZug.getSpalte(),
                    meineFarbe.opposite());
        }

        Zug naechsterZug;

        // Explorationsrate sinkt mit der Zeit, wir werden immer gieriger und weniger explorativ
        if (Math.random() > explorationRate) {
            naechsterZug = berechneGreedyZug();
        } else {
            naechsterZug = berechneRandomZug();
        }

        // State auf dessen Basis der Zug durchgeführt wurde speichern
        Spielfeld historyFeld = spielfeld.clone();

        spielHistory.add(new SpielHistoryEntry(historyFeld, naechsterZug));

        this.spielfeld.setFarbe(naechsterZug.getZeile(), naechsterZug.getSpalte(), meineFarbe);

        return naechsterZug;
    }

    private Zug berechneGreedyZug() {
        final double[] doubles = berechneQValue(spielfeld);
        int index;
        do {
            index = getIndexMaxQValue(doubles);
            doubles[index] = 0d;

        } while (spielfeld.getFarbe(getZeileForIndex(index), getSpalteForIndex(index)) != Farbe.Leer);
        return new Zug(getZeileForIndex(index), getSpalteForIndex(index));
    }

    private Zug berechneRandomZug() {
        final double[] doubles = berechneQValue(spielfeld);
        int index;
        do {
            index = getIndexRandomQValue(doubles);
            doubles[index] = 0d;

        } while (spielfeld.getFarbe(getZeileForIndex(index), getSpalteForIndex(index)) != Farbe.Leer);

        return new Zug(getZeileForIndex(index), getSpalteForIndex(index));
    }

    @Override
    public void neuesSpiel(final Farbe meineFarbe, final int bedenkzeitInSekunden) {
        this.meineFarbe = meineFarbe;
        spielfeld = new Spielfeld();
    }

    @Override
    public String getName() {
        return meinName;
    }

    @Override
    public void setName(String name) {
        meinName = name;
    }

    @Override
    public Farbe getFarbe() {
        return meineFarbe;
    }

    @Override
    public void setFarbe(final Farbe paramFarbe) {
        this.meineFarbe = paramFarbe;
    }

    @Override
    public Zug berechneZug(final Zug paramZug, final long paramLong1, final long paramLong2) {

        if (training) {
            return berechneLernZug(paramZug);
        }

        if (paramZug != null) {
            final Farbe gegner = meineFarbe.opposite();
            spielfeld.setFarbe(paramZug.getZeile(), paramZug.getSpalte(), gegner);
        }

        // Im kompetitiven Modus keine zufälligen Dinge tun
        final Zug naechsterZug = berechneGreedyZug();
        spielfeld.setFarbe(naechsterZug.getZeile(), naechsterZug.getSpalte(), meineFarbe);
        return naechsterZug;
    }

    private int getIndexMaxQValue(final double[] doubles) {
        final List<Double> doubleList = Arrays.asList(ArrayUtils.toObject(doubles));
        final Double max = Collections.max(doubleList);
        return doubleList.indexOf(max);
    }

    private int getIndexRandomQValue(final double[] doubles) {
        final double d = Math.random() * doubles.length;
        return (int) d;
    }

    private int getIndexForZug(final Zug zug) {
        return switch (zug.getZeile()) {
            case 0 -> zug.getSpalte();
            case 1 -> 3 + zug.getSpalte();
            case 2 -> 6 + zug.getSpalte();
            default -> throw new IllegalStateException();
        };
    }

    private int getZeileForIndex(int index) {
        return switch (index) {
            case 0, 1, 2 -> 0;
            case 3, 4, 5 -> 1;
            case 6, 7, 8 -> 2;
            default -> throw new IllegalStateException();
        };
    }

    private int getSpalteForIndex(final int index) {
        return switch (index) {
            case 0, 3, 6 -> 0;
            case 1, 4, 7 -> 1;
            case 2, 5, 8 -> 2;
            default -> -1;
        };
    }

    private double[] berechneQValue(final Spielfeld zustand) {
        mlp.setInput(convertToBinary(erzeugeInputVektor(zustand)));
        mlp.calculate();
        return mlp.getOutput();
    }

    private double[] erzeugeInputVektor(final Spielfeld zustand) {
        final double[] input = new double[9];
        int inputIndex = 0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                input[inputIndex] = getDoubleForFarbe(zustand.getFarbe(i, j));
                inputIndex++;
            }
        }
        return input;
    }

    private double getDoubleForFarbe(final Farbe farbe) {
        if (meineFarbe == farbe) {
            return 1d;
        }
        if (Farbe.Leer != farbe) {
            return -1d;
        }
        return 0d;
    }

}
