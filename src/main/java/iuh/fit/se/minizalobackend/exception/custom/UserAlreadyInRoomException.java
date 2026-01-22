package iuh.fit.se.minizalobackend.exception.custom;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class UserAlreadyInRoomException extends RuntimeException {
    public UserAlreadyInRoomException(String message) {
        super(message);
    }
}
