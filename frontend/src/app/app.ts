import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { AdminPwaService } from './core/pwa/admin-pwa.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly adminPwa = inject(AdminPwaService);

  constructor() {
    this.adminPwa.initialize();
  }
}
