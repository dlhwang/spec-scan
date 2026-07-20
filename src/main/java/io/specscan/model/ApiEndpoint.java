package io.specscan.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/** A fully described HTTP endpoint extracted from a Spring controller. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiEndpoint {
    /** e.g. AuthController.login */
    public String operationId;
    public String controller;
    /** Handler method signature, e.g. login(LoginDTO) */
    public String handler;
    public String summary;
    public String description;

    public String httpMethod;
    public String path;
    public String consumes;
    public String produces;
    public Integer successStatus;

    public List<ParamSpec> headers = new ArrayList<>();
    public List<ParamSpec> pathVariables = new ArrayList<>();
    public List<ParamSpec> queryParams = new ArrayList<>();

    public String requestBodyType;
    public TypeSchema requestBody;
    public String responseBodyType;
    public TypeSchema responseBody;

    public List<Condition> preConditions = new ArrayList<>();
    public List<Condition> responseAssertions = new ArrayList<>();
    public List<Condition> others = new ArrayList<>();

    /** file:line of the handler method */
    public String at;
}
