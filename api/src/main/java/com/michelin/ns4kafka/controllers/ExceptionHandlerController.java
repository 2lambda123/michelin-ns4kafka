package com.michelin.ns4kafka.controllers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import javax.validation.ConstraintViolationException;
import javax.validation.ElementKind;
import javax.validation.Path;

import com.michelin.ns4kafka.models.Status;
import com.michelin.ns4kafka.models.Status.StatusCause;
import com.michelin.ns4kafka.models.Status.StatusDetails;
import com.michelin.ns4kafka.models.Status.StatusPhase;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;

@Controller("/errors")
public class ExceptionHandlerController {

    @Error(global = true)
    public HttpResponse<Status> error(HttpRequest<?> request, ResourceValidationException exception) {
        var causes = exception.getValidationErrors().stream()
            .map(validationError -> (StatusCause.builder()
                .message(validationError)
                .build()))
            .collect(Collectors.toList());

        var status = Status.builder()
            .status(StatusPhase.Failed)
            .message(String.format("Invalid %s %s", exception.getKind(), exception.getName()))
            .reason("Invalid")
            .details(StatusDetails.builder()
                .kind(exception.getKind())
                .name(exception.getName())
                .causes(causes)
                .build())
            .code(HttpStatus.UNPROCESSABLE_ENTITY.getCode())
            .build();

        return HttpResponse.unprocessableEntity()
                .body(status);
    }

    @Error(global = true)
    public HttpResponse<Status> error(HttpRequest<?> request,ConstraintViolationException exception) {

        List<StatusCause> causes = new ArrayList<>();

        // as the function buildMessage in ConstraintExceptionHandler
        exception.getConstraintViolations().forEach(violation -> {
            Path propertyPath = violation.getPropertyPath();
            StringBuilder message = new StringBuilder();
            Iterator<Path.Node> i = propertyPath.iterator();
            while (i.hasNext()) {
                Path.Node node = i.next();
                if (node.getKind() == ElementKind.METHOD || node.getKind() == ElementKind.CONSTRUCTOR) {
                    continue;
                }
                message.append(node.getName());
                if (i.hasNext()) {
                    message.append('.');
                }
            }
            message.append(": ").append(violation.getMessage());
            causes.add(StatusCause.builder()
                       .message(message.toString())
                       .build());

        });

        var status = Status.builder()
            .status(StatusPhase.Failed)
            .message("Invalid Resource")
            .reason("Invalid")
            .details(StatusDetails.builder()
                .causes(causes)
                .build())
            .code(HttpStatus.UNPROCESSABLE_ENTITY.getCode())
            .build();

        return HttpResponse.unprocessableEntity()
                .body(status);

    }


    @Error(global = true)
    public HttpResponse<Status> error(HttpRequest<?> request, ResourceConflictException exception) {
        var causes = exception.getConflicts().stream()
            .map(conflict -> (StatusCause.builder()
                .message(conflict)
                .build()))
            .collect(Collectors.toList());

        var status = Status.builder()
            .status(StatusPhase.Failed)
            .message(String.format("Conflict of %s %s with other Resources", exception.getKind(), exception.getName()))
            .reason("Conflict")
            .details(StatusDetails.builder()
                .kind(exception.getKind())
                .name(exception.getName())
                .causes(causes)
                .build())
            .code(HttpStatus.CONFLICT.getCode())
            .build();

        return HttpResponse.status(HttpStatus.CONFLICT)
                .body(status);
    }


    @Error(global = true)
    public HttpResponse<Status> error(HttpRequest<?> request, ResourceNotFoundException exception) {
        var status = Status.builder()
            .status(StatusPhase.Failed)
            .message(String.format("Resource %s %s not found", exception.getKind(), exception.getName()))
            .reason("NotFound")
            .details(StatusDetails.builder()
                .kind(exception.getKind())
                .name(exception.getName())
                .build())
            .code(HttpStatus.NOT_FOUND.getCode())
            .build();

        return HttpResponse.notFound(status);
    }

    @Error(global = true)
    public HttpResponse<Status> error(HttpRequest<?> request, ResourceForbiddenException exception) {
        var status = Status.builder()
            .status(StatusPhase.Failed)
            .message(String.format("Resource %s %s forbidden", exception.getKind(), exception.getName()))
            .reason("Forbidden")
            .details(StatusDetails.builder()
                .kind(exception.getKind())
                .name(exception.getName())
                .build())
            .code(HttpStatus.FORBIDDEN.getCode())
            .build();

        return HttpResponse.status(HttpStatus.FORBIDDEN)
                .body(status);
    }

    // if we don't know the exception
    @Error(global = true)
    public HttpResponse<Status> error(HttpRequest<?> request, Exception exception) {
        var causes = exception.getMessage();

        var status = Status.builder()
            .status(StatusPhase.Failed)
            .message("Internal server error")
            .reason("InternalError")
            .details(StatusDetails.builder()
                .causes(List.of(StatusCause.builder()
                        .message(causes)
                        .build()))
                .build())
            .code(HttpStatus.INTERNAL_SERVER_ERROR.getCode())
            .build();
        return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(status);
    }

}
