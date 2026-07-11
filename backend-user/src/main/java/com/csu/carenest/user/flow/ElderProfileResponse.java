package com.csu.carenest.user.flow;

import java.util.List;

public record ElderProfileResponse(
        String elderId,
        String profileVersion,
        String name,
        String gender,
        String birthDate,
        String careLevel,
        List<EmergencyContactRequest> emergencyContacts
) {
}
