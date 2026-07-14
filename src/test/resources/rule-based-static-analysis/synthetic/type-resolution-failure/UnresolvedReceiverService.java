package fixtures.synthetic.unresolved;

public final class UnresolvedReceiverService {

    private final MissingCredentialVerifier verifier;

    public UnresolvedReceiverService(MissingCredentialVerifier verifier) {
        this.verifier = verifier;
    }

    public void verify(String supplied, String stored) {
        if (!verifier.matches(supplied, stored)) {
            throw new IllegalArgumentException("verification failed");
        }
    }
}

