package in.coreeye.coreneha.kernel;

import java.util.Optional;

/** Resolves the actor of the current request (implemented by the iam module). */
public interface CurrentActor {

    Optional<Actor> find();

    default Actor require() {
        return find().orElseThrow(() -> new UnauthenticatedException("Login required"));
    }
}
