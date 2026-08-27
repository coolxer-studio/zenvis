import { request } from '@/service/request-wrapper';
import {
  ConfigType,
  ConfigTreeNode,
  SchemaParams,
  SchemaResponse,
  TextContentParams,
  TextContentResponse,
  SaveJsonTextParams,
  SaveResponse,
  ApplyResponse,
  AddNodeParams,
  AddNodeResponse,
  DeleteNodeParams,
  DeleteNodeResponse,
  RenameNodeParams,
  RenameNodeResponse,
} from '@/types/type-policy';

const prefix = '/api/v1/config';

export class PolicyService {
  static async getConfig(type: ConfigType): Promise<ConfigTreeNode[]> {
    return request<ConfigTreeNode[]>(`${prefix}/${type}/tree`, '', 'GET');
  }

  static async schema(type: ConfigType, params: SchemaParams): Promise<SchemaResponse> {
    return request<SchemaResponse>(`${prefix}/${type}/schema`, params, 'GET');
  }

  static async textContent(type: ConfigType, params: TextContentParams): Promise<TextContentResponse> {
    return request<TextContentResponse>(`${prefix}/${type}/read`, params, 'GET');
  }

  static async saveJsonText(type: ConfigType, params: SaveJsonTextParams): Promise<SaveResponse> {
    return request<SaveResponse>(`${prefix}/${type}/modify`, params);
  }

  static async applyJsonText(type: ConfigType, params: SaveJsonTextParams): Promise<ApplyResponse> {
    return request<ApplyResponse>(`${prefix}/${type}/apply`, params);
  }

  static async addNode(type: ConfigType, params: AddNodeParams): Promise<AddNodeResponse> {
    return request<AddNodeResponse>(`${prefix}/${type}/add`, params);
  }

  static async deleteNode(type: ConfigType, params: DeleteNodeParams): Promise<DeleteNodeResponse> {
    return request<DeleteNodeResponse>(`${prefix}/${type}/delete`, params);
  }

  static async renameNode(type: ConfigType, params: RenameNodeParams): Promise<RenameNodeResponse> {
    return request<RenameNodeResponse>(`${prefix}/${type}/rename`, params);
  }
}