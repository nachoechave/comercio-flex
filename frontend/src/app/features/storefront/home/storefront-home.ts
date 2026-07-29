import { Component, inject, OnInit } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-storefront-home',
  imports: [RouterLink],
  templateUrl: './storefront-home.html',
  styleUrl: './storefront-home.scss',
})
export class StorefrontHome implements OnInit {
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);

  ngOnInit(): void {
    this.title.setTitle('Comercio Flex');
    this.meta.updateTag({
      name: 'description',
      content: 'Comercio Flex, tiendas online simples y configurables para comercios.',
    });
  }
}
