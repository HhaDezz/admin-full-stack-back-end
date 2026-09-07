package com.atelier.api.dto.response;

import java.util.List;

/** Shown once. Only hashes are stored, so they can't be displayed again. */
public record RecoveryCodesResponse(List<String> codes) {
}
