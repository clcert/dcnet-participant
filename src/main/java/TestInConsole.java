import dcnet.DCNETProtocol;

import java.io.IOException;
import java.util.Observable;
import java.util.Observer;

public class TestInConsole {

    /**
     * Usage: ./gradlew run -PappArgs=[{message},{directoryIP},{cheatingMode}]
     *
     * @param args message and ip address of directory node
     * @throws IOException test
     */
    public static void main(String[] args) throws IOException {
        // Parse arguments
        String message = args[0];
        String directoryIp = args[1];
        boolean cheaterNode = Boolean.parseBoolean(args[2]);

        DCNETProtocol dcnetProtocol = new DCNETProtocol();
        ParticipantsLeftToConnectObserver participantsLeftToConnectObserver = new ParticipantsLeftToConnectObserver(dcnetProtocol.getObservableParticipantsLeft());
        dcnetProtocol.getObservableParticipantsLeft().addObserver(participantsLeftToConnectObserver);
        MessagesArrivedObserver messagesArrivedObserver = new MessagesArrivedObserver(dcnetProtocol.getObservableMessageArrived());
        dcnetProtocol.getObservableMessageArrived().addObserver(messagesArrivedObserver);

        System.err.println("Connecting to Room " + directoryIp + "...");
        dcnetProtocol.connectToDirectory(directoryIp);

        System.err.println("Participant IP: " + dcnetProtocol.getNodeIp());

        System.err.println("PARTICIPANT NODE " + dcnetProtocol.getNodeIndex() + " of " + dcnetProtocol.getRoomSize());
        if (message.equals("")) {
            System.err.println("\nP_" + dcnetProtocol.getNodeIndex() + " doesn't want to communicate any message\n");
        } else
            System.err.println("\nm_" + dcnetProtocol.getNodeIndex() + " = " + message + "\n");

        dcnetProtocol.setMessageToSend(message, cheaterNode);
        dcnetProtocol.runProtocol();

        System.err.println("\nTotal Time: " + dcnetProtocol.getTotalTime() + " seconds");
        System.err.println("Time to get first message: " + dcnetProtocol.getFirstMessageTime() + " seconds");
        System.err.println("Average time per message: " + dcnetProtocol.getAverageTimePerMessage() + " seconds");
        System.err.println("Real rounds played: " + dcnetProtocol.getNumberOfRealRounds());

        System.out.println(dcnetProtocol.getTotalTime() + "," + dcnetProtocol.getFirstMessageTime() + "," + dcnetProtocol.getAverageTimePerMessage());
        System.out.println("Sync Time: " + dcnetProtocol.getSyncTime() + " (" + dcnetProtocol.getSyncTime() * 100.0 / dcnetProtocol.getTotalTime() + "%)");

    }

    private static class ParticipantsLeftToConnectObserver implements Observer {

        private DCNETProtocol.ObservableParticipantsLeft observableParticipantsLeft;

        public ParticipantsLeftToConnectObserver(DCNETProtocol.ObservableParticipantsLeft observableParticipantsLeft) {
            this.observableParticipantsLeft = observableParticipantsLeft;
        }

        @Override
        public void update(Observable observable, Object data) {
            if (observable == observableParticipantsLeft) {
                final int participantsLeftToConnect = observableParticipantsLeft.getValue();
                if (participantsLeftToConnect == -1) {
                    System.err.println("Connected to Room!");
                } else {
                    if (participantsLeftToConnect == 1) {
                        System.err.println("Waiting " + participantsLeftToConnect + " participant to join room");
                    } else if (participantsLeftToConnect != 0) {
                        System.err.println("Waiting " + participantsLeftToConnect + " participants to join room");
                    }
                }
            }
        }
    }

    private static class MessagesArrivedObserver implements Observer {

        private DCNETProtocol.ObservableMessageArrived observableMessageArrived;

        public MessagesArrivedObserver(DCNETProtocol.ObservableMessageArrived observableMessageArrived) {
            this.observableMessageArrived = observableMessageArrived;
        }

        @Override
        public void update(Observable observable, Object data) {
            if (observable == observableMessageArrived) {
                final String messageArrivedValue = observableMessageArrived.getValue();
                System.err.println("ANON: " + messageArrivedValue);
            }
        }

    }

}
