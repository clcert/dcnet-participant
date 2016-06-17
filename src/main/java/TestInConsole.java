import dcnet.DCNETProtocol;

import java.io.IOException;
import java.util.Observable;
import java.util.Observer;

public class TestInConsole {

    /**
     * Usage: ./gradlew run -PappArgs=[{message},{directoryIP},{cheatingMode}]
     * @param args message and ip address of directory node
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

        System.out.println("Connecting to Room " + directoryIp + "...");
        dcnetProtocol.connectToDirectory(directoryIp);

        System.out.println("Participant IP: " + dcnetProtocol.getNodeIp());

        System.out.println("PARTICIPANT NODE " + dcnetProtocol.getNodeIndex() + " of " + dcnetProtocol.getRoomSize());
        if (message.equals("")) {
            System.out.println("\nP_" + dcnetProtocol.getNodeIndex() + " doesn't want to communicate any message\n");
        }
        else
            System.out.println("\nm_" + dcnetProtocol.getNodeIndex() + " = " + message + "\n");

        dcnetProtocol.setMessageToSend(message, cheaterNode);
        dcnetProtocol.runProtocol();

        System.out.println("\nTotal Time: " + dcnetProtocol.getTotalTime() + " seconds");
        System.out.println("Time to get first message: " + dcnetProtocol.getFirstMessageTime() + " seconds");
        System.out.println("Average time per message: " + dcnetProtocol.getAverageTimePerMessage() + " seconds");
        System.out.println("Real rounds played: " + dcnetProtocol.getNumberOfRealRounds());

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
                    System.out.println("Connected to Room!");
                }
                else {
                    if (participantsLeftToConnect == 1) {
                        System.out.println("Waiting " + participantsLeftToConnect + " participant to join room");
                    }
                    else if (participantsLeftToConnect != 0) {
                        System.out.println("Waiting " + participantsLeftToConnect + " participants to join room");
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
                System.out.println("ANON: " + messageArrivedValue);
            }
        }

    }

}
