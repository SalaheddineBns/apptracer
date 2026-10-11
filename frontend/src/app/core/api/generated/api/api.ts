export * from './authentication.service';
import { AuthenticationService } from './authentication.service';
export * from './users.service';
import { UsersService } from './users.service';
export const APIS = [AuthenticationService, UsersService];
