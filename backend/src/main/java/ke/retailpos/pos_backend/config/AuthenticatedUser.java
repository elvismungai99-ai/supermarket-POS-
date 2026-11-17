package ke.retailpos.pos_backend.config;

import java.util.UUID;

public record AuthenticatedUser(
    UUID userId,
    String username,
    String role
) {}
