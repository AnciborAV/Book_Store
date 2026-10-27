package ru.aav.config;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import ru.aav.exception.EntityNotFoundException;

@RestControllerAdvice
public class ControllerAdviceExceptionHandler
    extends ResponseEntityExceptionHandler
{

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ProblemDetail handleInvalidParameters(
        jakarta.validation.ConstraintViolationException exception
    ) {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Проверьте параметры запроса"
        );
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleNotFound(EntityNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            exception.getMessage()
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleConflict(
        DataIntegrityViolationException exception
    ) {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "Запись конфликтует с существующими данными. Проверьте уникальность названия книги или адреса почты."
        );
    }
}
