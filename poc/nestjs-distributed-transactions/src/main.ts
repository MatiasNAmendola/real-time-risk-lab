import 'reflect-metadata';
import { ValidationPipe } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import { AppModule } from '@cmd/app.module';
import { startOpenTelemetry } from '@infrastructure/observability/otel';

async function bootstrap(): Promise<void> {
  startOpenTelemetry();
  const app = await NestFactory.create(AppModule, { bufferLogs: true });
  app.useGlobalPipes(new ValidationPipe({ whitelist: true, transform: true }));
  const config = new DocumentBuilder()
    .setTitle('Real-Time Risk Lab — NestJS Distributed Transactions')
    .setDescription('Saga rollback, TigerBeetle ledger boundary, CQRS and Event Sourcing examples.')
    .setVersion('0.1.0')
    .build();
  SwaggerModule.setup('/docs', app, SwaggerModule.createDocument(app, config));
  await app.listen(Number(process.env.PORT ?? 3001));
}

void bootstrap();
