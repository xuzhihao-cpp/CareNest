import type { components } from './generated/user-api';

type Schemas = components['schemas'];

export type Gender = 'MALE' | 'FEMALE' | 'UNKNOWN';

export type CareLevel = 'LEVEL_1' | 'LEVEL_2' | 'LEVEL_3';

export type RelationType = 'SON' | 'DAUGHTER' | 'SPOUSE' | 'OTHER';

export type ElderProfileScenario = 'normal' | 'empty' | 'error';

export type EmergencyContact = Schemas['EmergencyContactRequest'];
export type ElderProfileRequest = Schemas['ElderProfileRequest'];
export type ElderProfileResponse = Schemas['ElderProfileResponse'];
export type FamilyElderListResult = Schemas['ElderProfileResponse'][];
