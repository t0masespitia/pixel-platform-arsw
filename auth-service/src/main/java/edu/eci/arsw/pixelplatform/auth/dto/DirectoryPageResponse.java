package edu.eci.arsw.pixelplatform.auth.dto;

import java.util.List;

public record DirectoryPageResponse(
        List<UserDirectoryEntry> users, int page, int size,
        long totalElements, int totalPages
) {}
