/*
 Navicat Premium Dump SQL

 Source Server         : v而保护
 Source Server Type    : PostgreSQL
 Source Server Version : 180004 (180004)
 Source Host           : localhost:5432
 Source Catalog        : crushCupid
 Source Schema         : public

 Target Server Type    : PostgreSQL
 Target Server Version : 180004 (180004)
 File Encoding         : 65001

 Date: 01/09/2026 13:40:43
*/


-- ----------------------------
-- Sequence structure for ai_provider_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."ai_provider_id_seq";
CREATE SEQUENCE "public"."ai_provider_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for chat_media_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."chat_media_id_seq";
CREATE SEQUENCE "public"."chat_media_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for chat_source_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."chat_source_id_seq";
CREATE SEQUENCE "public"."chat_source_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for conversation_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."conversation_id_seq";
CREATE SEQUENCE "public"."conversation_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for crush_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."crush_id_seq";
CREATE SEQUENCE "public"."crush_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for crush_report_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."crush_report_id_seq";
CREATE SEQUENCE "public"."crush_report_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for crush_version_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."crush_version_id_seq";
CREATE SEQUENCE "public"."crush_version_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Table structure for ai_provider
-- ----------------------------
DROP TABLE IF EXISTS "public"."ai_provider";
CREATE TABLE "public"."ai_provider" (
  "id" int8 NOT NULL DEFAULT nextval('ai_provider_id_seq'::regclass),
  "name" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "provider_key" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "base_url" varchar(512) COLLATE "pg_catalog"."default" NOT NULL,
  "api_key" varchar(512) COLLATE "pg_catalog"."default",
  "model" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "temperature" float8 DEFAULT 0.7,
  "top_p" float8,
  "max_tokens" int4,
  "capabilities" varchar(200) COLLATE "pg_catalog"."default" DEFAULT ''::character varying,
  "is_default" bool DEFAULT false,
  "created_at" timestamptz(6) DEFAULT now(),
  "updated_at" timestamptz(6) DEFAULT now()
)
;

-- ----------------------------
-- Table structure for chat_media
-- ----------------------------
DROP TABLE IF EXISTS "public"."chat_media";
CREATE TABLE "public"."chat_media" (
  "id" int8 NOT NULL DEFAULT nextval('chat_media_id_seq'::regclass),
  "crush_id" int8 NOT NULL,
  "role" varchar(20) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'user'::character varying,
  "media_url" text COLLATE "pg_catalog"."default" NOT NULL,
  "media_type" varchar(50) COLLATE "pg_catalog"."default" DEFAULT 'image'::character varying,
  "created_at" timestamptz(6) DEFAULT now()
)
;

-- ----------------------------
-- Table structure for chat_source
-- ----------------------------
DROP TABLE IF EXISTS "public"."chat_source";
CREATE TABLE "public"."chat_source" (
  "id" int8 NOT NULL DEFAULT nextval('chat_source_id_seq'::regclass),
  "crush_id" int8 NOT NULL,
  "file_name" varchar(255) COLLATE "pg_catalog"."default",
  "file_path" varchar(500) COLLATE "pg_catalog"."default",
  "file_type" varchar(50) COLLATE "pg_catalog"."default",
  "file_format" varchar(20) COLLATE "pg_catalog"."default",
  "message_count" int4 DEFAULT 0,
  "raw_analysis" jsonb,
  "parsed_at" timestamptz(6),
  "created_at" timestamptz(6) DEFAULT now(),
  "content" text COLLATE "pg_catalog"."default"
)
;

-- ----------------------------
-- Table structure for conversation
-- ----------------------------
DROP TABLE IF EXISTS "public"."conversation";
CREATE TABLE "public"."conversation" (
  "id" int8 NOT NULL DEFAULT nextval('conversation_id_seq'::regclass),
  "crush_id" int8 NOT NULL,
  "role" varchar(20) COLLATE "pg_catalog"."default" NOT NULL,
  "content" text COLLATE "pg_catalog"."default" NOT NULL,
  "created_at" timestamptz(6) DEFAULT now()
)
;

-- ----------------------------
-- Table structure for crush
-- ----------------------------
DROP TABLE IF EXISTS "public"."crush";
CREATE TABLE "public"."crush" (
  "id" int8 NOT NULL DEFAULT nextval('crush_id_seq'::regclass),
  "name" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "slug" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "mbti" varchar(10) COLLATE "pg_catalog"."default",
  "zodiac" varchar(20) COLLATE "pg_catalog"."default",
  "occupation" varchar(100) COLLATE "pg_catalog"."default",
  "gender" varchar(20) COLLATE "pg_catalog"."default",
  "know_duration" varchar(50) COLLATE "pg_catalog"."default",
  "relationship_status" varchar(50) COLLATE "pg_catalog"."default",
  "impression" text COLLATE "pg_catalog"."default",
  "persona_layer0" text COLLATE "pg_catalog"."default",
  "persona_layer1" text COLLATE "pg_catalog"."default",
  "persona_layer2" text COLLATE "pg_catalog"."default",
  "persona_layer3" text COLLATE "pg_catalog"."default",
  "persona_layer4" text COLLATE "pg_catalog"."default",
  "memory_overview" text COLLATE "pg_catalog"."default",
  "memory_timeline" jsonb,
  "memory_sweet" text COLLATE "pg_catalog"."default",
  "memory_interaction" text COLLATE "pg_catalog"."default",
  "current_stage" int2 DEFAULT 1,
  "total_messages" int4 DEFAULT 0,
  "last_chat_date" timestamptz(6),
  "version" int4 DEFAULT 1,
  "created_at" timestamptz(6) DEFAULT now(),
  "updated_at" timestamptz(6) DEFAULT now(),
  "status" varchar(20) COLLATE "pg_catalog"."default" DEFAULT 'DRAFT'::character varying,
  "voice_id" varchar(100) COLLATE "pg_catalog"."default",
  "proactive_enabled" bool DEFAULT true,
  "next_proactive_at" timestamptz(6),
  "last_proactive_at" timestamptz(6),
  "proactive_date" date,
  "proactive_count" int4 DEFAULT 0
)
;

-- ----------------------------
-- Table structure for crush_report
-- ----------------------------
DROP TABLE IF EXISTS "public"."crush_report";
CREATE TABLE "public"."crush_report" (
  "id" int8 NOT NULL DEFAULT nextval('crush_report_id_seq'::regclass),
  "crush_id" int8 NOT NULL,
  "crush_name" varchar(100) COLLATE "pg_catalog"."default",
  "title" varchar(255) COLLATE "pg_catalog"."default",
  "markdown" text COLLATE "pg_catalog"."default" NOT NULL,
  "source" varchar(20) COLLATE "pg_catalog"."default" DEFAULT 'manual'::character varying,
  "report_date" date,
  "created_at" timestamptz(6) DEFAULT now()
)
;

-- ----------------------------
-- Table structure for crush_version
-- ----------------------------
DROP TABLE IF EXISTS "public"."crush_version";
CREATE TABLE "public"."crush_version" (
  "id" int8 NOT NULL DEFAULT nextval('crush_version_id_seq'::regclass),
  "crush_id" int8 NOT NULL,
  "version" int4 NOT NULL,
  "snapshot" jsonb NOT NULL,
  "reason" varchar(500) COLLATE "pg_catalog"."default",
  "created_at" timestamptz(6) DEFAULT now()
)
;

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."ai_provider_id_seq"
OWNED BY "public"."ai_provider"."id";
SELECT setval('"public"."ai_provider_id_seq"', 3, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."chat_media_id_seq"
OWNED BY "public"."chat_media"."id";
SELECT setval('"public"."chat_media_id_seq"', 7, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."chat_source_id_seq"
OWNED BY "public"."chat_source"."id";
SELECT setval('"public"."chat_source_id_seq"', 5, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."conversation_id_seq"
OWNED BY "public"."conversation"."id";
SELECT setval('"public"."conversation_id_seq"', 15422, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."crush_id_seq"
OWNED BY "public"."crush"."id";
SELECT setval('"public"."crush_id_seq"', 3, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."crush_report_id_seq"
OWNED BY "public"."crush_report"."id";
SELECT setval('"public"."crush_report_id_seq"', 1, false);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."crush_version_id_seq"
OWNED BY "public"."crush_version"."id";
SELECT setval('"public"."crush_version_id_seq"', 4, true);

-- ----------------------------
-- Indexes structure for table ai_provider
-- ----------------------------
CREATE INDEX "idx_ai_provider_key" ON "public"."ai_provider" USING btree (
  "provider_key" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Uniques structure for table ai_provider
-- ----------------------------
ALTER TABLE "public"."ai_provider" ADD CONSTRAINT "ai_provider_provider_key_key" UNIQUE ("provider_key");

-- ----------------------------
-- Primary Key structure for table ai_provider
-- ----------------------------
ALTER TABLE "public"."ai_provider" ADD CONSTRAINT "ai_provider_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table chat_media
-- ----------------------------
CREATE INDEX "idx_chat_media_crush" ON "public"."chat_media" USING btree (
  "crush_id" "pg_catalog"."int8_ops" ASC NULLS LAST,
  "created_at" "pg_catalog"."timestamptz_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table chat_media
-- ----------------------------
ALTER TABLE "public"."chat_media" ADD CONSTRAINT "chat_media_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table chat_source
-- ----------------------------
CREATE INDEX "idx_chat_source_crush" ON "public"."chat_source" USING btree (
  "crush_id" "pg_catalog"."int8_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table chat_source
-- ----------------------------
ALTER TABLE "public"."chat_source" ADD CONSTRAINT "chat_source_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table conversation
-- ----------------------------
CREATE INDEX "idx_conversation_crush" ON "public"."conversation" USING btree (
  "crush_id" "pg_catalog"."int8_ops" ASC NULLS LAST,
  "created_at" "pg_catalog"."timestamptz_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table conversation
-- ----------------------------
ALTER TABLE "public"."conversation" ADD CONSTRAINT "conversation_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Uniques structure for table crush
-- ----------------------------
ALTER TABLE "public"."crush" ADD CONSTRAINT "crush_slug_key" UNIQUE ("slug");

-- ----------------------------
-- Primary Key structure for table crush
-- ----------------------------
ALTER TABLE "public"."crush" ADD CONSTRAINT "crush_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table crush_report
-- ----------------------------
CREATE INDEX "idx_crush_report_crush" ON "public"."crush_report" USING btree (
  "crush_id" "pg_catalog"."int8_ops" ASC NULLS LAST,
  "report_date" "pg_catalog"."date_ops" DESC NULLS FIRST
);

-- ----------------------------
-- Primary Key structure for table crush_report
-- ----------------------------
ALTER TABLE "public"."crush_report" ADD CONSTRAINT "crush_report_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table crush_version
-- ----------------------------
CREATE INDEX "idx_version_crush" ON "public"."crush_version" USING btree (
  "crush_id" "pg_catalog"."int8_ops" ASC NULLS LAST,
  "version" "pg_catalog"."int4_ops" DESC NULLS FIRST
);

-- ----------------------------
-- Primary Key structure for table crush_version
-- ----------------------------
ALTER TABLE "public"."crush_version" ADD CONSTRAINT "crush_version_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Foreign Keys structure for table chat_media
-- ----------------------------
ALTER TABLE "public"."chat_media" ADD CONSTRAINT "chat_media_crush_id_fkey" FOREIGN KEY ("crush_id") REFERENCES "public"."crush" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

-- ----------------------------
-- Foreign Keys structure for table chat_source
-- ----------------------------
ALTER TABLE "public"."chat_source" ADD CONSTRAINT "chat_source_crush_id_fkey" FOREIGN KEY ("crush_id") REFERENCES "public"."crush" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

-- ----------------------------
-- Foreign Keys structure for table conversation
-- ----------------------------
ALTER TABLE "public"."conversation" ADD CONSTRAINT "conversation_crush_id_fkey" FOREIGN KEY ("crush_id") REFERENCES "public"."crush" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

-- ----------------------------
-- Foreign Keys structure for table crush_report
-- ----------------------------
ALTER TABLE "public"."crush_report" ADD CONSTRAINT "crush_report_crush_id_fkey" FOREIGN KEY ("crush_id") REFERENCES "public"."crush" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

-- ----------------------------
-- Foreign Keys structure for table crush_version
-- ----------------------------
ALTER TABLE "public"."crush_version" ADD CONSTRAINT "crush_version_crush_id_fkey" FOREIGN KEY ("crush_id") REFERENCES "public"."crush" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;
