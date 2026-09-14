package com.airline.servlet;

import com.airline.dao.FlightDAO;
import com.airline.model.Flight;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RESTful Servlet controller for the Flight Management Module.
 * Handles CRUD operations, search/filtering, and dashboard analytics.
 * Returns clean JSON responses with standard success/error envelopes.
 */
@WebServlet(name = "FlightServlet", urlPatterns = {"/api/flights", "/api/flights/*"})
public class FlightServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private FlightDAO flightDAO;
    private Gson gson;

    @Override
    public void init() throws ServletException {
        super.init();
        this.flightDAO = new FlightDAO();
        this.gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        System.out.println("[FlightServlet] Initialized successfully.");
    }

    /**
     * Sets standard CORS and JSON headers.
     */
    private void setCommonHeaders(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setCommonHeaders(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * Handles HTTP GET requests:
     * - ?action=list (or default) -> List all flights
     * - ?action=get&id={flightId} -> Single flight details
     * - ?action=search&from={city}&to={city}&date={date} -> Search & filter flights
     * - ?action=stats -> Dashboard KPI metrics
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setCommonHeaders(response);
        PrintWriter out = response.getWriter();
        String action = request.getParameter("action");
        if (action == null || action.trim().isEmpty()) {
            action = "list";
        }

        try {
            switch (action.toLowerCase()) {
                case "get":
                    handleGetFlight(request, response, out);
                    break;
                case "search":
                    handleSearchFlights(request, response, out);
                    break;
                case "stats":
                    handleGetStatistics(request, response, out);
                    break;
                case "list":
                default:
                    handleListFlights(request, response, out);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, out, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Server error: " + e.getMessage());
        }
    }

    /**
     * Handles HTTP POST requests:
     * - ?action=create -> Add new flight
     * - ?action=update -> Update flight info
     * - ?action=delete&id={id} -> Delete flight
     * - ?action=cancel&id={id} -> Soft-cancel flight
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setCommonHeaders(response);
        PrintWriter out = response.getWriter();
        String action = request.getParameter("action");

        if (action == null || action.trim().isEmpty()) {
            sendError(response, out, HttpServletResponse.SC_BAD_REQUEST, "Missing required 'action' parameter.");
            return;
        }

        try {
            switch (action.toLowerCase()) {
                case "create":
                    handleCreateFlight(request, response, out);
                    break;
                case "update":
                    handleUpdateFlight(request, response, out);
                    break;
                case "delete":
                    handleDeleteFlight(request, response, out);
                    break;
                case "cancel":
                    handleCancelFlight(request, response, out);
                    break;
                default:
                    sendError(response, out, HttpServletResponse.SC_BAD_REQUEST, "Unknown action: " + action);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, out, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Server error: " + e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        setCommonHeaders(response);
        PrintWriter out = response.getWriter();
        handleDeleteFlight(request, response, out);
    }

    // --- Action Handlers ---

    private void handleListFlights(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        List<Flight> flights = flightDAO.getAllFlights();
        Map<String, Object> respMap = new HashMap<>();
        respMap.put("success", true);
        respMap.put("count", flights.size());
        respMap.put("data", flights);
        out.print(gson.toJson(respMap));
    }

    private void handleGetFlight(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        String idStr = request.getParameter("id");
        if (idStr == null || idStr.trim().isEmpty()) {
            sendError(response, out, HttpServletResponse.SC_BAD_REQUEST, "Missing required parameter 'id'.");
            return;
        }

        try {
            int id = Integer.parseInt(idStr.trim());
            Flight flight = flightDAO.getFlightById(id);
            if (flight == null) {
                sendError(response, out, HttpServletResponse.SC_NOT_FOUND, "Flight with ID " + id + " not found.");
                return;
            }

            Map<String, Object> respMap = new HashMap<>();
            respMap.put("success", true);
            respMap.put("data", flight);
            out.print(gson.toJson(respMap));
        } catch (NumberFormatException e) {
            sendError(response, out, HttpServletResponse.SC_BAD_REQUEST, "Invalid flight ID format.");
        }
    }

    private void handleSearchFlights(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        String from = request.getParameter("from");
        String to = request.getParameter("to");
        String date = request.getParameter("date");

        List<Flight> flights = flightDAO.searchFlights(from, to, date);
        Map<String, Object> respMap = new HashMap<>();
        respMap.put("success", true);
        respMap.put("count", flights.size());
        respMap.put("data", flights);
        out.print(gson.toJson(respMap));
    }

    private void handleGetStatistics(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        Map<String, Object> stats = flightDAO.getFlightStatistics();
        Map<String, Object> respMap = new HashMap<>();
        respMap.put("success", true);
        respMap.put("data", stats);
        out.print(gson.toJson(respMap));
    }

    private void handleCreateFlight(HttpServletRequest request, HttpServletResponse response, PrintWriter out)
            throws IOException {
        Flight flight = parseFlightFromRequest(request);

        // Model validation
        List<String> validationErrors = flight.validate();
        if (!validationErrors.isEmpty()) {
            sendValidationErrors(response, out, validationErrors);
            return;
        }

        // Check flight number uniqueness
        if (flightDAO.isFlightNumberExists(flight.getFlightNumber(), 0)) {
            sendError(response, out, HttpServletResponse.SC_CONFLICT,
                    "Flight number '" + flight.getFlightNumber() + "' is already registered in the system.");
            return;
        }

        boolean created = flightDAO.addFlight(flight);
        if (created) {
            response.setStatus(HttpServletResponse.SC_CREATED);
            Map<String, Object> respMap = new HashMap<>();
            respMap.put("success", true);
            respMap.put("message", "Flight " + flight.getFlightNumber() + " successfully created.");
            respMap.put("data", flight);
            out.print(gson.toJson(respMap));
        } else {
            sendError(response, out, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to save flight to database.");
        }
    }

    private void handleUpdateFlight(HttpServletRequest request, HttpServletResponse response, PrintWriter out)
            throws IOException {
        Flight flight = parseFlightFromRequest(request);

        if (flight.getFlightId() <= 0) {
            sendError(response, out, HttpServletResponse.SC_BAD_REQUEST, "Valid 'flightId' is required for updates.");
            return;
        }

        // Verify flight exists
        Flight existing = flightDAO.getFlightById(flight.getFlightId());
        if (existing == null) {
            sendError(response, out, HttpServletResponse.SC_NOT_FOUND, "Flight with ID " + flight.getFlightId() + " not found.");
            return;
        }

        // Model validation
        List<String> validationErrors = flight.validate();
        if (!validationErrors.isEmpty()) {
            sendValidationErrors(response, out, validationErrors);
            return;
        }

        // Check flight number uniqueness excluding current flight ID
        if (flightDAO.isFlightNumberExists(flight.getFlightNumber(), flight.getFlightId())) {
            sendError(response, out, HttpServletResponse.SC_CONFLICT,
                    "Flight number '" + flight.getFlightNumber() + "' is used by another flight.");
            return;
        }

        boolean updated = flightDAO.updateFlight(flight);
        if (updated) {
            Map<String, Object> respMap = new HashMap<>();
            respMap.put("success", true);
            respMap.put("message", "Flight " + flight.getFlightNumber() + " updated successfully.");
            respMap.put("data", flight);
            out.print(gson.toJson(respMap));
        } else {
            sendError(response, out, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to update flight in database.");
        }
    }

    private void handleDeleteFlight(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        String idStr = request.getParameter("id");
        if (idStr == null || idStr.trim().isEmpty()) {
            sendError(response, out, HttpServletResponse.SC_BAD_REQUEST, "Missing required parameter 'id'.");
            return;
        }

        try {
            int id = Integer.parseInt(idStr.trim());
            Flight flight = flightDAO.getFlightById(id);
            if (flight == null) {
                sendError(response, out, HttpServletResponse.SC_NOT_FOUND, "Flight with ID " + id + " not found.");
                return;
            }

            boolean deleted = flightDAO.deleteFlight(id);
            if (deleted) {
                Map<String, Object> respMap = new HashMap<>();
                respMap.put("success", true);
                respMap.put("message", "Flight " + flight.getFlightNumber() + " was deleted successfully.");
                out.print(gson.toJson(respMap));
            } else {
                sendError(response, out, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to delete flight.");
            }
        } catch (NumberFormatException e) {
            sendError(response, out, HttpServletResponse.SC_BAD_REQUEST, "Invalid flight ID format.");
        }
    }

    private void handleCancelFlight(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
        String idStr = request.getParameter("id");
        if (idStr == null || idStr.trim().isEmpty()) {
            sendError(response, out, HttpServletResponse.SC_BAD_REQUEST, "Missing required parameter 'id'.");
            return;
        }

        try {
            int id = Integer.parseInt(idStr.trim());
            Flight flight = flightDAO.getFlightById(id);
            if (flight == null) {
                sendError(response, out, HttpServletResponse.SC_NOT_FOUND, "Flight with ID " + id + " not found.");
                return;
            }

            boolean cancelled = flightDAO.cancelFlight(id);
            if (cancelled) {
                Map<String, Object> respMap = new HashMap<>();
                respMap.put("success", true);
                respMap.put("message", "Flight " + flight.getFlightNumber() + " was marked as Cancelled.");
                out.print(gson.toJson(respMap));
            } else {
                sendError(response, out, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to cancel flight.");
            }
        } catch (NumberFormatException e) {
            sendError(response, out, HttpServletResponse.SC_BAD_REQUEST, "Invalid flight ID format.");
        }
    }

    // --- Helper Methods ---

    /**
     * Parses a Flight object from either application/json body or application/x-www-form-urlencoded params.
     */
    private Flight parseFlightFromRequest(HttpServletRequest request) throws IOException {
        String contentType = request.getContentType();

        if (contentType != null && contentType.toLowerCase().contains("application/json")) {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = request.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            return gson.fromJson(sb.toString(), Flight.class);
        }

        // Fallback to URL-encoded form parameters
        Flight flight = new Flight();
        String idStr = request.getParameter("flightId");
        if (idStr != null && !idStr.trim().isEmpty()) {
            try {
                flight.setFlightId(Integer.parseInt(idStr.trim()));
            } catch (NumberFormatException ignored) {}
        }

        flight.setFlightNumber(request.getParameter("flightNumber"));
        flight.setAirlineName(request.getParameter("airlineName"));
        flight.setDepartureAirport(request.getParameter("departureAirport"));
        flight.setArrivalAirport(request.getParameter("arrivalAirport"));
        flight.setDepartureCity(request.getParameter("departureCity"));
        flight.setArrivalCity(request.getParameter("arrivalCity"));
        flight.setDepartureDate(request.getParameter("departureDate"));
        flight.setDepartureTime(request.getParameter("departureTime"));
        flight.setArrivalDate(request.getParameter("arrivalDate"));
        flight.setArrivalTime(request.getParameter("arrivalTime"));
        flight.setAircraftType(request.getParameter("aircraftType"));

        String totalSeatsStr = request.getParameter("totalSeats");
        if (totalSeatsStr != null && !totalSeatsStr.trim().isEmpty()) {
            try {
                flight.setTotalSeats(Integer.parseInt(totalSeatsStr.trim()));
            } catch (NumberFormatException ignored) {}
        }

        String availSeatsStr = request.getParameter("availableSeats");
        if (availSeatsStr != null && !availSeatsStr.trim().isEmpty()) {
            try {
                flight.setAvailableSeats(Integer.parseInt(availSeatsStr.trim()));
            } catch (NumberFormatException ignored) {}
        }

        String priceStr = request.getParameter("ticketPrice");
        if (priceStr != null && !priceStr.trim().isEmpty()) {
            try {
                flight.setTicketPrice(new BigDecimal(priceStr.trim()));
            } catch (NumberFormatException ignored) {}
        }

        flight.setStatus(request.getParameter("status"));
        return flight;
    }

    private void sendError(HttpServletResponse response, PrintWriter out, int status, String message) {
        response.setStatus(status);
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("success", false);
        errorMap.put("message", message);
        out.print(gson.toJson(errorMap));
    }

    private void sendValidationErrors(HttpServletResponse response, PrintWriter out, List<String> errors) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("success", false);
        errorMap.put("message", "Validation failed. Please correct the highlighted errors.");
        errorMap.put("errors", errors);
        out.print(gson.toJson(errorMap));
    }
}
