package finance.admin;

import org.junit.jupiter.api.AfterEach;import org.junit.jupiter.api.Test;import java.util.UUID;import static org.junit.jupiter.api.Assertions.*;
class AdminOperationGuardTest {@AfterEach void clear(){AdminOperationGuard.clear();}@Test void destructiveActionRequiresMatchingSecondRequest(){UUID id=UUID.randomUUID();assertFalse(AdminOperationGuard.confirm(id,"CLEAR","all",100));assertFalse(AdminOperationGuard.confirm(id,"CLEAR","other",101));assertTrue(AdminOperationGuard.confirm(id,"CLEAR","all",102));assertFalse(AdminOperationGuard.confirm(id,"CLEAR","all",400));}}
