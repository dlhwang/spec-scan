package fixtures.synthetic.falsepositive;

public final class UnrelatedMatchesService {

    public void requireReferenceFormat(String reference) {
        if (!reference.matches("[A-Z]{3}-[0-9]{4}")) {
            throw new IllegalArgumentException("invalid reference format");
        }
    }
}

