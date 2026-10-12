export * from './activityTypes.service';
import { ActivityTypesService } from './activityTypes.service';
export * from './authentication.service';
import { AuthenticationService } from './authentication.service';
export * from './projects.service';
import { ProjectsService } from './projects.service';
export * from './users.service';
import { UsersService } from './users.service';
export const APIS = [ActivityTypesService, AuthenticationService, ProjectsService, UsersService];
